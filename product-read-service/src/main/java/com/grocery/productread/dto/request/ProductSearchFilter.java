package com.grocery.productread.dto.request;

import com.grocery.productread.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Everything a product search can filter on. Every field is optional except statuses.
 * Controllers build it: customers always get statuses = ACTIVE, a vendor always gets their own storeId.
 *
 * @param text         free text, matched against name, brand, category name and description
 * @param statuses     which statuses to include
 * @param inStockOnly  only products with at least one available unit
 * @param lowStockOnly only products at or below their low-stock threshold (vendor and admin panels)
 */
public record ProductSearchFilter(
        String text,
        Long categoryId,
        Long storeId,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Set<ProductStatus> statuses,
        boolean inStockOnly,
        boolean lowStockOnly) {
}
