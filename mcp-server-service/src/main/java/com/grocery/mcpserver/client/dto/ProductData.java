package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A product as GET /api/v1/products answers it (ProductResponse of product-read-service), only the fields the tools use.
 *
 * @param price             before the discount (MRP)
 * @param sellingPrice      what the customer pays
 * @param availableQuantity units customers can still buy
 * @param lowStock          at or below the store's low-stock threshold
 * @param ratingAverage     null without reviews
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductData(
        Long id,
        Long storeId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String brand,
        String unit,
        BigDecimal unitQuantity,
        BigDecimal price,
        BigDecimal discountPercentage,
        BigDecimal sellingPrice,
        int availableQuantity,
        boolean inStock,
        boolean lowStock,
        BigDecimal ratingAverage,
        int ratingCount) {
}
