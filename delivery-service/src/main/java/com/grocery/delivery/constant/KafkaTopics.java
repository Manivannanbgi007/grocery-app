package com.grocery.delivery.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 */
public final class KafkaTopics {

    /** Published by this service (outbox aggregate type "delivery"), key = orderId. */
    public static final String DELIVERY_EVENTS = "grocery.delivery.events";
    public static final String DELIVERY_AGGREGATE = "delivery";

    /** Consumed: UserRegistered (new DELIVERY_PARTNER users get a partner profile). */
    public static final String USER_EVENTS = "grocery.user.events";

    /** Consumed: OrderReadyForPickup (a new delivery), OrderCancelled (cancel it if not picked up yet). */
    public static final String ORDER_EVENTS = "grocery.order.events";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
