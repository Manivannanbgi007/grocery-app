#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Creates every Kafka topic used by the grocery platform.
#
# Auto topic creation is disabled on the broker, so a topic must be
# listed here before a service can use it (a typo in a topic name then
# fails loudly instead of silently creating a new topic).
#
# Runs inside the Kafka container. scripts/start-infra.ps1 calls it for you:
#   docker exec grocery-kafka bash /scripts/create-topics.sh
# Safe to run many times (--if-not-exists).
# ---------------------------------------------------------------------
set -euo pipefail

BOOTSTRAP_SERVER="localhost:9092"
PARTITIONS=3

# Durability: how many brokers keep a copy of every partition, and how many copies must have an event before Kafka
# confirms it to a producer with acks=all (Debezium and the dead-letter producers use acks=all).
#   Local (one broker):         REPLICATION_FACTOR=1, MIN_ISR=1 (the defaults below)
#   Production (3+ brokers):    REPLICATION_FACTOR=3, MIN_ISR=2 -> one broker can fail without losing an event
#                               or stopping the producers.
# Pass them in to change them:  docker exec -e REPLICATION_FACTOR=3 -e MIN_ISR=2 grocery-kafka bash /scripts/create-topics.sh
# (The replication factor of an existing topic does not change this way; that needs a partition reassignment.)
REPLICATION_FACTOR="${REPLICATION_FACTOR:-1}"
MIN_ISR="${MIN_ISR:-1}"

# How long Kafka keeps events before deleting them. A consumer that is further behind than this loses events, so
# every consumer must be back well within it (alert KafkaConsumerNoProgress fires after minutes).
DOMAIN_RETENTION_MS=$((7 * 24 * 60 * 60 * 1000))    # 7 days
DLT_RETENTION_MS=$((30 * 24 * 60 * 60 * 1000))      # 30 days: time to look at a failed event and replay it

# Domain events, published through each service's transactional outbox
# (Debezium Outbox Event Router). Message key = aggregate id.
DOMAIN_EVENT_TOPICS=(
  grocery.user.events
  grocery.store.events
  grocery.inventory.events
  grocery.order.events
  grocery.payment.events
  grocery.delivery.events
  grocery.platform.events
)

# Row-level change events captured by Debezium from product_db
# (product-service -> product-read-service, catalog CQRS).
CDC_TOPICS=(
  cdc.product_db.products
  cdc.product_db.categories
)

# Debezium heartbeats, one topic per connector (__debezium-heartbeat.<topic.prefix of the connector>). Every 10 s each
# connector writes one small message here and so saves its binlog position, even when its own database has no
# changes. Without it, a quiet database's saved position falls out of the 7-day binlog and the connector cannot start.
HEARTBEAT_TOPICS=(
  __debezium-heartbeat.auth-outbox
  __debezium-heartbeat.store-outbox
  __debezium-heartbeat.inventory-outbox
  __debezium-heartbeat.order-outbox
  __debezium-heartbeat.payment-outbox
  __debezium-heartbeat.delivery-outbox
  __debezium-heartbeat.platform-outbox
  __debezium-heartbeat.cdc
)

# $1 topic, $2 partitions, $3 settings (--add-config list). Creating is skipped when the topic exists; the settings
# are applied every time, so existing topics get changed settings too.
create_topic() {
  kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --create --if-not-exists \
    --topic "$1" --partitions "$2" --replication-factor "$REPLICATION_FACTOR"
  kafka-configs --bootstrap-server "$BOOTSTRAP_SERVER" --alter --entity-type topics \
    --entity-name "$1" --add-config "$3"
}

for topic in "${DOMAIN_EVENT_TOPICS[@]}"; do
  create_topic "$topic"     "$PARTITIONS" "retention.ms=$DOMAIN_RETENTION_MS,min.insync.replicas=$MIN_ISR"
done

# CDC topics are COMPACTED: Kafka keeps the latest change of every row (key = row id) forever,
# instead of deleting old messages after 7 days. Replaying such a topic from the start therefore
# always rebuilds the complete read model (see scripts/rebuild-product-index.ps1).
for topic in "${CDC_TOPICS[@]}"; do
  create_topic "$topic"     "$PARTITIONS" "cleanup.policy=compact,min.insync.replicas=$MIN_ISR"
done

# Dead-letter topics: messages that keep failing end up here (kept 30 days; inspect and replay: docs/resilience.md)
for topic in "${DOMAIN_EVENT_TOPICS[@]}" "${CDC_TOPICS[@]}"; do
  create_topic "$topic.DLT" "$PARTITIONS" "retention.ms=$DLT_RETENTION_MS,min.insync.replicas=$MIN_ISR"
done

# Heartbeats: one partition is enough; compacted, so only the last heartbeat of a connector is kept
for topic in "${HEARTBEAT_TOPICS[@]}"; do
  create_topic "$topic"     1 "cleanup.policy=compact"
done

echo "Application topics in the cluster (the event catalog lists their settings: docs/event-catalog.md):"
kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list | grep -v '^_'
