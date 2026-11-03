package com.grocery.store.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 *
 * The outbox "aggregate type" decides the topic: Debezium routes an outbox row with
 * aggregate_type = "store" to the topic grocery.store.events (see debezium/*.json).
 */
public final class KafkaTopics {

    public static final String STORE_EVENTS = "grocery.store.events";

    /** Aggregate type of the events this service publishes. */
    public static final String STORE_AGGREGATE = "store";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
