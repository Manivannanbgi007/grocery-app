package com.grocery.payment.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 */
public final class KafkaTopics {

    /** Published by this service (outbox aggregate type "payment"). */
    public static final String PAYMENT_EVENTS = "grocery.payment.events";
    public static final String PAYMENT_AGGREGATE = "payment";

    /** Consumed: OrderCreated, OrderCancelled, OrderRejected, OrderDelivered. */
    public static final String ORDER_EVENTS = "grocery.order.events";

    /** Consumed: StockReserved. */
    public static final String INVENTORY_EVENTS = "grocery.inventory.events";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
