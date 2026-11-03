package com.grocery.cart.service;

import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.response.CouponResponse;
import org.springframework.data.domain.Pageable;

/**
 * Admins create PLATFORM coupons (valid in every store) and can switch any coupon on or off.
 */
public interface AdminCouponService {

    /** storeId = null: every coupon; platformOnly = true: only platform coupons. */
    PageResponse<CouponResponse> searchCoupons(Long storeId, boolean platformOnly, Pageable pageable);

    CouponResponse getCoupon(Long couponId);

    CouponResponse createPlatformCoupon(Long adminUserId, CouponRequest request);

    /** Only platform coupons; store coupons belong to their vendor. */
    CouponResponse updatePlatformCoupon(Long couponId, CouponRequest request);

    /** Any coupon, e.g. to stop a misleading store offer. */
    CouponResponse changeStatus(Long couponId, boolean active);
}
