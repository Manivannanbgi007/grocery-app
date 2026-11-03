package com.grocery.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What Razorpay Checkout hands to the browser after a successful payment; the frontend sends it on unchanged.
 */
public record RazorpayVerifyRequest(
        @NotBlank @Size(max = 100) String razorpayOrderId,
        @NotBlank @Size(max = 100) String razorpayPaymentId,
        @NotBlank @Size(max = 200) String razorpaySignature) {
}
