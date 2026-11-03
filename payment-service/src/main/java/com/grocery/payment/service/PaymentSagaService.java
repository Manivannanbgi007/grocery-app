package com.grocery.payment.service;

import com.grocery.events.inventory.StockReserved;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;

/**
 * How payments react to the other services' events (saga choreography).
 * Every method is idempotent: a duplicate event is skipped, an event that no longer fits is ignored.
 */
public interface PaymentSagaService {

    /** Creates the PENDING payment of a new order. */
    void handleOrderCreated(OrderCreated event);

    /**
     * The stock is reserved:
     *   COD    -> CASH_ON_DELIVERY + PaymentConfirmed (the order is confirmed right away)
     *   ONLINE -> AWAITING_PAYMENT (the customer pays now)
     */
    void handleStockReserved(StockReserved event);

    /** OrderCancelled or OrderRejected: refund a paid payment (RefundCompleted), otherwise just cancel it. */
    void handleOrderCancelled(String eventId, String eventType, Long orderId);

    /** COD: the cash was collected at the door. */
    void handleOrderDelivered(OrderDelivered event);
}
