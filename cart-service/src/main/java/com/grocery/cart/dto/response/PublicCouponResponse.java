package com.grocery.cart.dto.response;

import com.grocery.cart.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What customers see on the offers page (no usage numbers).
 *
 * @param storeId null = valid in every store
 */
public record PublicCouponResponse(
        String code,
        String description,
        Long storeId,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Instant validUntil) {
}
