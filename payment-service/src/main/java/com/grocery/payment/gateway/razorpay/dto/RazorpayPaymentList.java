package com.grocery.payment.gateway.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Razorpay's answer to GET /orders/{id}/payments: every payment attempt of the order.
 * {"count": 2, "items": [{"id": "pay_A", "status": "failed"}, {"id": "pay_B", "status": "captured"}]}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayPaymentList(List<Item> items) {

    public RazorpayPaymentList {
        items = items == null ? List.of() : items;
    }

    /** @param status created, authorized, captured, refunded or failed */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String status) {
    }
}
