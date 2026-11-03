package com.grocery.cart.dto.response;

import com.grocery.cart.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Full coupon details for vendors and admins.
 *
 * @param storeId null = platform coupon
 */
public record CouponResponse(
        Long id,
        String code,
        String description,
        Long storeId,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Instant validFrom,
        Instant validUntil,
        Integer usageLimit,
        int usedCount,
        int perCustomerLimit,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
