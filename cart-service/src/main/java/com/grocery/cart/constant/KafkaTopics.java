package com.grocery.cart.constant;

/**
 * Kafka topics this service reads (see docs/event-catalog.md). cart-service publishes no events.
 */
public final class KafkaTopics {

    /** Consumed: OrderCreated (empty the cart, count the coupon use), OrderCancelled (free the coupon use). */
    public static final String ORDER_EVENTS = "grocery.order.events";

    /** Consumed: PlatformSettingsChanged (new delivery fee, free delivery amount, minimum order). */
    public static final String PLATFORM_EVENTS = "grocery.platform.events";

    private KafkaTopics() {
    }
}
