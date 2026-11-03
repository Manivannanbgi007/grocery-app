package com.grocery.cart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The product fields cart-service needs from product-read-service.
 * The response has more fields; ignoreUnknown skips them.
 *
 * @param status            ACTIVE, INACTIVE, BLOCKED or DELETED
 * @param availableQuantity units customers can still buy (from inventory-service, via the search index)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProduct(
        Long id,
        Long storeId,
        String name,
        String unit,
        BigDecimal unitQuantity,
        BigDecimal price,
        BigDecimal sellingPrice,
        BigDecimal taxPercentage,
        List<String> imageUrls,
        String status,
        int availableQuantity) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public String firstImageUrl() {
        return imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.getFirst();
    }
}
