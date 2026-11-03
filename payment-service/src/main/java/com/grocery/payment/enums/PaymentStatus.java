package com.grocery.payment.enums;

/**
 * The life of a payment.
 *
 *   PENDING ──StockReserved──► CASH_ON_DELIVERY (COD) ──OrderDelivered──► COLLECTED
 *      │                └──► AWAITING_PAYMENT (ONLINE) ──pay──► PAID ──order cancelled──► REFUNDED
 *      │                                              └──► FAILED (declined, or not paid in time)
 *      └── order cancelled before anything was paid ──► CANCELLED
 *
 *   FAILED / CANCELLED ──the money arrives anyway (late)──► PAID ──refunded at once──► REFUNDED
 */
public enum PaymentStatus {
    /** Created from OrderCreated; waiting until the stock is reserved. */
    PENDING,
    /** ONLINE: the stock is reserved, the customer can pay now. */
    AWAITING_PAYMENT,
    /** ONLINE: paid. */
    PAID,
    /** ONLINE: the payment failed; the order is cancelled. */
    FAILED,
    /** COD: confirmed; cash is collected at the door. */
    CASH_ON_DELIVERY,
    /** COD: the cash was collected (order delivered). */
    COLLECTED,
    /** The order was cancelled before anything was paid. */
    CANCELLED,
    /** The money of a cancelled or rejected order went back to the customer. */
    REFUNDED
}
