package com.grocery.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The part of a Razorpay webhook we read. Example (shortened):
 * <pre>
 * {"event": "payment.captured",
 *  "payload": {"payment": {"entity": {"id": "pay_29QQ...", "order_id": "order_9A33...", "status": "captured",
 *                                     "amount": 15800, "currency": "INR"}}}}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayWebhookEvent(String event, Payload payload) {

    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_FAILED = "payment.failed";

    /** The payment the event is about, or null for events without one. */
    public PaymentEntity payment() {
        return payload == null || payload.payment() == null ? null : payload.payment().entity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(PaymentHolder payment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentHolder(PaymentEntity entity) {
    }

    /**
     * @param amount   in paise (the smallest unit of INR): 158.00 rupees = 15800
     * @param currency "INR"
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentEntity(
            String id,
            @JsonProperty("order_id") String orderId,
            String status,
            Long amount,
            String currency,
            @JsonProperty("error_description") String errorDescription) {
    }
}
