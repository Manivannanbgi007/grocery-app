package com.grocery.payment.service;

import com.grocery.payment.dto.response.PaymentResponse;

/**
 * The customer's own payments. Every method takes the customer's user id (X-User-Id).
 */
public interface CustomerPaymentService {

    PaymentResponse getMyPayment(Long customerUserId, Long orderId);

    /** Mock gateway only: pay (success = true) or have the bank decline it (success = false). */
    PaymentResponse payWithMockGateway(Long customerUserId, Long orderId, boolean success);
}
