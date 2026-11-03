package com.grocery.cart.dto.response;

import java.math.BigDecimal;

/**
 * @param price        one unit before the product discount (MRP)
 * @param sellingPrice one unit, what the customer pays
 * @param lineTotal    sellingPrice x quantity
 */
public record CartItemResponse(
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
