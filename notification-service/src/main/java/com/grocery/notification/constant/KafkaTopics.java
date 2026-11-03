package com.grocery.notification.constant;

/**
 * Kafka topics this service reads (see docs/event-catalog.md). It publishes nothing.
 */
public final class KafkaTopics {

    public static final String USER_EVENTS = "grocery.user.events";
    public static final String STORE_EVENTS = "grocery.store.events";
    public static final String ORDER_EVENTS = "grocery.order.events";
    public static final String PAYMENT_EVENTS = "grocery.payment.events";
    public static final String INVENTORY_EVENTS = "grocery.inventory.events";
    public static final String DELIVERY_EVENTS = "grocery.delivery.events";

    private KafkaTopics() {
    }
}
