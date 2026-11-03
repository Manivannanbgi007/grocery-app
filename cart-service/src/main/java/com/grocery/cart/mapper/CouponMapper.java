package com.grocery.cart.mapper;

import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.response.CouponResponse;
import com.grocery.cart.dto.response.PublicCouponResponse;
import com.grocery.cart.entity.Coupon;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CouponMapper {

    /**
     * @param storeId null for a platform coupon
     */
    public Coupon toEntity(CouponRequest request, Long storeId, Long createdByUserId) {
        Coupon coupon = new Coupon();
        coupon.setStoreId(storeId);
        coupon.setCreatedByUserId(createdByUserId);
        coupon.setUsedCount(0);
        coupon.setActive(true);
        copyDetails(request, coupon);
        return coupon;
    }

    public void copyDetails(CouponRequest request, Coupon coupon) {
        coupon.setCode(normalizeCode(request.code()));
        coupon.setDescription(request.description().trim());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setPerCustomerLimit(request.perCustomerLimitOrDefault());
    }

    /** Codes are stored in upper case: "welcome50" and "WELCOME50" are the same coupon. */
    public String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getStoreId(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                coupon.getValidFrom(),
                coupon.getValidUntil(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.getPerCustomerLimit(),
                coupon.isActive(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt());
    }

    public PublicCouponResponse toPublicResponse(Coupon coupon) {
        return new PublicCouponResponse(
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getStoreId(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                coupon.getValidUntil());
    }
}
