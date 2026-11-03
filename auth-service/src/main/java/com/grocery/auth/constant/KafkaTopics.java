package com.grocery.auth.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 *
 * The outbox "aggregate type" decides the topic: Debezium routes an outbox row with
 * aggregate_type = "user" to the topic grocery.user.events (see debezium/*.json).
 */
public final class KafkaTopics {

    public static final String USER_EVENTS = "grocery.user.events";
    public static final String STORE_EVENTS = "grocery.store.events";

    /** Aggregate type of the events this service publishes. */
    public static final String USER_AGGREGATE = "user";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
