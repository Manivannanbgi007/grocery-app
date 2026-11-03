package com.grocery.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Razorpay's answer to POST /payments/{id}/refund, e.g. {"id": "rfnd_FP8...", "status": "processed", ...}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayRefund(String id, String status) {
}
