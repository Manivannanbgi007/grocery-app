package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A coupon as GET /api/v1/coupons answers it (PublicCouponResponse of cart-service).
 *
 * @param storeId      null = valid in every store
 * @param discountType PERCENTAGE or FLAT
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CouponData(
        String code,
        String description,
        Long storeId,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Instant validUntil) {
}
