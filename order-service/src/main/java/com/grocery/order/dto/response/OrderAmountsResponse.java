package com.grocery.order.dto.response;

import java.math.BigDecimal;

/**
 * totalAmount = itemsTotal - couponDiscount + deliveryFee (all prices include tax).
 */
public record OrderAmountsResponse(
        BigDecimal itemsTotal,
        String couponCode,
        BigDecimal couponDiscount,
        BigDecimal deliveryFee,
        BigDecimal taxIncluded,
        BigDecimal totalAmount) {
}
