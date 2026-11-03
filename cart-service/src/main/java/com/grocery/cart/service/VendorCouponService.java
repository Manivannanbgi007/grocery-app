package com.grocery.cart.service;

import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.response.CouponResponse;
import org.springframework.data.domain.Pageable;

/**
 * Coupons of the vendor's OWN store. Every method takes the vendor's storeId.
 */
public interface VendorCouponService {

    PageResponse<CouponResponse> listMyCoupons(Long storeId, Pageable pageable);

    CouponResponse getMyCoupon(Long storeId, Long couponId);

    CouponResponse createCoupon(Long storeId, Long vendorUserId, CouponRequest request);

    CouponResponse updateCoupon(Long storeId, Long couponId, CouponRequest request);

    CouponResponse changeStatus(Long storeId, Long couponId, boolean active);
}
