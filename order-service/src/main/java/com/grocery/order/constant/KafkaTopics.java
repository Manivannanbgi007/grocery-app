package com.grocery.order.constant;

/**
 * Kafka topic names and outbox aggregate types (see docs/event-catalog.md).
 */
public final class KafkaTopics {

    /** Published by this service (outbox aggregate type "order"). */
    public static final String ORDER_EVENTS = "grocery.order.events";
    public static final String ORDER_AGGREGATE = "order";

    /** Consumed: StockReservationFailed. */
    public static final String INVENTORY_EVENTS = "grocery.inventory.events";

    /** Consumed: PaymentConfirmed, PaymentFailed, RefundCompleted. */
    public static final String PAYMENT_EVENTS = "grocery.payment.events";

    /** Consumed: DeliveryAssigned, DeliveryPickedUp, DeliveryCompleted, DeliveryFailed. */
    public static final String DELIVERY_EVENTS = "grocery.delivery.events";

    private KafkaTopics() {
    }

    public static String forAggregate(String aggregateType) {
        return "grocery." + aggregateType + ".events";
    }
}
