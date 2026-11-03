package com.grocery.product.dto.response;

import com.grocery.product.enums.ProductStatus;
import com.grocery.product.enums.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The product as saved in MySQL, returned after a write.
 * (Searching and listing products is done by product-read-service.)
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
        Unit unit,
        BigDecimal unitQuantity,
        BigDecimal price,
        BigDecimal discountPercentage,
        BigDecimal sellingPrice,
        BigDecimal taxPercentage,
        List<String> imageUrls,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
