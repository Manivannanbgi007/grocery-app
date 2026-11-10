#!/bin/bash
# -----------------------------------------------------------------------------
# Registers the Debezium connectors with Kafka Connect, run as the ECS task
# "kafka-connectors" (ecs-bootstrap.tf) once the services are running (their
# tables must exist). Safe to run again: PUT .../config creates or updates.
#
# This file holds the helper functions. ecs-bootstrap.tf appends one
# `put_connector <name> <<'JSON' ... JSON` call per file in <service>/debezium/,
# then `finish`.
#
# Environment: CONNECT_URL, INVENTORY_URL, PUBLISH_SEED_STOCK ("true"/"false")
# -----------------------------------------------------------------------------
set -euo pipefail

echo "Waiting for Kafka Connect at $CONNECT_URL ..."
for attempt in $(seq 1 60); do
  if curl -fsS "$CONNECT_URL/connectors" > /dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "Kafka Connect did not answer within 5 minutes. Is the kafka-connect service running?" >&2
    exit 1
  fi
  sleep 5
done

# put_connector <name>: the connector's settings (JSON) come on standard input
put_connector() {
  echo "Registering $1"
  curl -fsS -X PUT -H "Content-Type: application/json" --data @- "$CONNECT_URL/connectors/$1/config" > /dev/null
}

finish() {
  echo "Waiting 15 seconds for the connectors to start ..."
  sleep 15
  failed=0
  for name in $(curl -fsS "$CONNECT_URL/connectors" | tr -d '[]"' | tr ',' ' '); do
    status="$(curl -fsS "$CONNECT_URL/connectors/$name/status")"
    echo "$name: $status"
    if echo "$status" | grep -q '"FAILED"'; then
      failed=1
    fi
  done

  # The sample stock is inserted by SQL, without events: publish it once, so the
  # search shows it (the same as the README's step 3 on a laptop)
  if [ "${PUBLISH_SEED_STOCK:-false}" = "true" ]; then
    echo "Publishing the sample stock ..."
    curl -fsS -X POST "$INVENTORY_URL/internal/inventory/stock-snapshot"
    echo
  fi

  if [ "$failed" -ne 0 ]; then
    echo "At least one connector FAILED: see its status above and the kafka-connect logs." >&2
    exit 1
  fi
  echo "Done: every connector is running."
}
