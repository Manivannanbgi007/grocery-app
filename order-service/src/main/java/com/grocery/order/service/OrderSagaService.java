package com.grocery.order.service;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.DeliveryCompleted;
import com.grocery.events.delivery.DeliveryFailed;
import com.grocery.events.delivery.DeliveryPickedUp;
import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;

/**
 * How orders react to the other services' events (saga choreography - no central coordinator).
 * Every method is idempotent: a duplicate event is skipped, an event that no longer fits is ignored.
 */
public interface OrderSagaService {

    /** No stock -> CANCELLED (OrderCancelled; payment-service then cancels the payment). */
    void handleStockReservationFailed(StockReservationFailed event);

    /** Stock reserved + payment settled -> CONFIRMED (OrderConfirmed): the store sees the order. */
    void handlePaymentConfirmed(PaymentConfirmed event);

    /** Online payment failed -> CANCELLED (OrderCancelled; inventory then releases the stock). */
    void handlePaymentFailed(PaymentFailed event);

    /** The money of a cancelled or rejected order went back to the customer. */
    void handleRefundCompleted(RefundCompleted event);

    /** A delivery partner took the job: the order shows who delivers it. */
    void handleDeliveryAssigned(DeliveryAssigned event);

    /** The partner collected the order -> OUT_FOR_DELIVERY (OrderOutForDelivery). */
    void handleDeliveryPickedUp(DeliveryPickedUp event);

    /** The customer received it -> DELIVERED (OrderDelivered: inventory commits, payment marks COD collected). */
    void handleDeliveryCompleted(DeliveryCompleted event);

    /** The partner could not deliver -> DELIVERY_FAILED (OrderDeliveryFailed: stock back, payment refunded or cancelled). */
    void handleDeliveryFailed(DeliveryFailed event);
}
