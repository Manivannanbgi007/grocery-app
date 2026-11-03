package com.grocery.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Razorpay's answer to POST /orders (only the fields we need), e.g. {"id": "order_N5x9...", "status": "created", ...}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayOrder(String id, String status) {
}
