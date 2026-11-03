package com.grocery.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * cart-service's checkout summary: the cart checked against today's catalog.
 * An order is only placed when readyForCheckout is true.
 *
 * @param couponCode          the coupon whose discount is in the totals (null = none)
 * @param checkoutFingerprint changes with any product, quantity, price, coupon or total of the cart
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutSummary(
        Long customerUserId,
        Long storeId,
        List<Item> items,
        String couponCode,
        Totals totals,
        boolean readyForCheckout,
        List<String> problems,
        String checkoutFingerprint) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long productId,
            String productName,
            String unit,
            BigDecimal unitQuantity,
            String imageUrl,
            BigDecimal price,
            BigDecimal sellingPrice,
            BigDecimal taxPercentage,
            int quantity,
            BigDecimal lineTotal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            BigDecimal itemsTotal,
            BigDecimal couponDiscount,
            BigDecimal deliveryFee,
            BigDecimal payableAmount,
            BigDecimal taxIncluded) {
    }
}
