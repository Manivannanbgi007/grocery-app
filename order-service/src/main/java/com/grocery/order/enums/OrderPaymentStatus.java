package com.grocery.order.enums;

/**
 * The payment as the order shows it (payment-service has the full details).
 */
public enum OrderPaymentStatus {
    /** Not settled yet (the saga is still running). */
    PENDING,
    /** Paid online, or cash collected at the door. */
    PAID,
    /** Cash on delivery accepted: to be paid at the door. */
    CASH_ON_DELIVERY,
    FAILED,
    /** The order was cancelled or rejected before anything was paid: nothing is owed. */
    CANCELLED,
    /** The money went back to the customer (cancelled or rejected after paying). */
    REFUNDED
}
