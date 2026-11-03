package com.grocery.inventory.dto.response;

import java.time.Instant;

/**
 * Stock of one product. Product names are not stored here: the vendor's product list
 * (product-read-service) shows names together with these stock numbers.
 */
public record StockResponse(
        Long productId,
        Long storeId,
        int quantityOnHand,
        int reservedQuantity,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock,
        boolean outOfStock,
        Instant updatedAt) {
}
