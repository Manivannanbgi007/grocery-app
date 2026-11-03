package com.grocery.inventory.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 *
 * The outbox "aggregate type" decides the topic: Debezium routes an outbox row with
 * aggregate_type = "inventory" to the topic grocery.inventory.events (see debezium/*.json).
 * The message key is the aggregate id: the productId for stock events, the orderId for reservation events.
 */
public final class KafkaTopics {

    public static final String INVENTORY_EVENTS = "grocery.inventory.events";

    /** Aggregate type of the events this service publishes. */
    public static final String INVENTORY_AGGREGATE = "inventory";

    /** Consumed: OrderCreated (reserve), OrderCancelled / OrderRejected (release), OrderDelivered (commit). */
    public static final String ORDER_EVENTS = "grocery.order.events";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
