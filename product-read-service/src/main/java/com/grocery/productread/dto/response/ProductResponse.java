package com.grocery.productread.dto.response;

import com.grocery.productread.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param availableQuantity units customers can still buy (from inventory-service)
 * @param inStock           availableQuantity > 0
 * @param lowStock          at or below the store's low-stock threshold ("only a few left")
 * @param ratingAverage     average stars of the visible reviews (null without reviews)
 */
public record ProductResponse(
        Long id,
        Long storeId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String brand,
        String sku,
        String unit,
        BigDecimal unitQuantity,
        BigDecimal price,
        BigDecimal discountPercentage,
        BigDecimal sellingPrice,
        BigDecimal taxPercentage,
        List<String> imageUrls,
        ProductStatus status,
        int availableQuantity,
        boolean inStock,
        boolean lowStock,
        BigDecimal ratingAverage,
        int ratingCount,
        Instant updatedAt) {
}
