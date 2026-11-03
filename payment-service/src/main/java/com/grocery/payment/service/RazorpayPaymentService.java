package com.grocery.payment.service;

import com.grocery.payment.dto.request.RazorpayVerifyRequest;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.RazorpayCheckoutResponse;

/**
 * Paying with Razorpay (only when app.payment.gateway = razorpay):
 *
 *   1. frontend: GET checkout details -> opens Razorpay Checkout -> the customer pays
 *   2. frontend: POST verify with what Checkout returned -> PAID (PaymentConfirmed)
 *   3. Razorpay: webhook payment.captured -> PAID too (in case the browser closed before step 2)
 *
 * Steps 2 and 3 may both arrive; whichever comes second finds the payment PAID and does nothing.
 */
public interface RazorpayPaymentService {

    RazorpayCheckoutResponse getCheckout(Long customerUserId, Long orderId);

    PaymentResponse verifyCheckout(Long customerUserId, Long orderId, RazorpayVerifyRequest request);

    /**
     * @param rawBody   the request body exactly as received (the signature is calculated over these bytes)
     * @param signature header X-Razorpay-Signature
     * @param eventId   header X-Razorpay-Event-Id, used to skip a webhook Razorpay sends again
     * @return what happened, for the response message and the log
     */
    String handleWebhook(String rawBody, String signature, String eventId);
}
