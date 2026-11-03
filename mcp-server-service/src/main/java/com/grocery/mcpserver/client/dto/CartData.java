package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The customer's cart from cart-service (GET /api/v1/cart), only the fields the tools need.
 * Prices include GST; the totals are calculated by cart-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CartData(Long storeId, List<Item> items, String couponCode, String couponMessage, Totals totals) {

    /** false for an empty cart (it then has no store either) */
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    /** How many packs of this product the cart has (0 when it is not in the cart) */
    public int packsOf(long productId) {
        return items == null ? 0 : items.stream()
                .filter(item -> item.productId() != null && item.productId() == productId)
                .mapToInt(Item::quantity)
                .sum();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productId, String productName, String unit, BigDecimal unitQuantity, BigDecimal sellingPrice,
                       int quantity, BigDecimal lineTotal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(BigDecimal itemsTotal, BigDecimal productSavings, BigDecimal couponDiscount, BigDecimal deliveryFee,
                         BigDecimal payableAmount, BigDecimal minimumOrderValue, boolean minimumOrderMet) {
    }
}
