package com.grocery.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productName,
        String unit,
        BigDecimal unitQuantity,
        String imageUrl,
        BigDecimal price,
        BigDecimal unitPrice,
        BigDecimal taxPercentage,
        int quantity,
        BigDecimal lineTotal) {
}
