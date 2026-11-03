package com.grocery.payment.dto.response;

import java.time.Instant;

/**
 * Everything the frontend passes to Razorpay Checkout (checkout.js) to open the payment window.
 *
 * @param keyId          the public key id (never the secret)
 * @param razorpayOrderId the order to pay against (order_...)
 * @param amountInPaise  158.00 rupees = 15800
 * @param payBefore      after this time the payment fails and the order is cancelled
 */
public record RazorpayCheckoutResponse(
        String keyId,
        String razorpayOrderId,
        long amountInPaise,
        String currency,
        String description,
        Instant payBefore) {
}
