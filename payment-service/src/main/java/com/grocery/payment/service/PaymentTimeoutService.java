package com.grocery.payment.service;

import java.util.List;

/**
 * Online payments that are not paid before their deadline (payBefore) fail, so the order is cancelled
 * and its reserved stock is released. Called by PaymentTimeoutJob.
 */
public interface PaymentTimeoutService {

    /** Ids of AWAITING_PAYMENT payments whose deadline has passed. */
    List<Long> findOverduePaymentIds();

    /**
     * Ends one overdue payment in its own transaction: PAID if the gateway has the money after all
     * (its message got lost), otherwise FAILED.
     */
    void expire(Long paymentId);
}
