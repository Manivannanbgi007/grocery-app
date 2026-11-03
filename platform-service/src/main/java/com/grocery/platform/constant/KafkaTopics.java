package com.grocery.platform.constant;

/**
 * Kafka topic names used by platform-service (created by infrastructure/kafka/create-topics.sh).
 */
public final class KafkaTopics {

    /** Published (outbox): PlatformSettingsChanged -> grocery.platform.events */
    public static final String PLATFORM_AGGREGATE = "platform";

    /** Read for the audit log (every domain topic except our own). */
    public static final String USER_EVENTS = "grocery.user.events";
    public static final String STORE_EVENTS = "grocery.store.events";
    public static final String INVENTORY_EVENTS = "grocery.inventory.events";
    public static final String ORDER_EVENTS = "grocery.order.events";
    public static final String PAYMENT_EVENTS = "grocery.payment.events";
    public static final String DELIVERY_EVENTS = "grocery.delivery.events";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
