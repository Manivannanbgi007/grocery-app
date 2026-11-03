package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One order of the customer from order-service (GET /api/v1/orders/{id}), only the fields the tools need.
 * The delivery address and phone numbers are left out on purpose: the assistant does not need them.
 *
 * @param paymentUrl where the customer pays an online order that is not paid yet (the website's order page, e.g.
 *                   "/orders/12"), as order-service sends it; null when nothing is to be paid online
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderData(
        long id,
        String storeName,
        String status,
        String statusReason,
        String paymentMethod,
        String paymentStatus,
        String paymentUrl,
        List<Item> items,
        Amounts amounts,
        boolean cancellable,
        String deliveryPartnerName,
        List<Step> timeline,
        Instant placedAt,
        Instant deliveredAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String productName, String unit, BigDecimal unitQuantity, BigDecimal unitPrice, int quantity,
                       BigDecimal lineTotal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amounts(BigDecimal itemsTotal, String couponCode, BigDecimal couponDiscount, BigDecimal deliveryFee,
                          BigDecimal totalAmount) {
    }

    /** One step of the order's history, e.g. PACKED "Your items are packed". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String status, String note, Instant at) {
    }
}
