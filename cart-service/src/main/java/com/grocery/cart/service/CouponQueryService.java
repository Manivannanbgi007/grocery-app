package com.grocery.cart.service;

import com.grocery.cart.dto.response.PublicCouponResponse;

import java.util.List;

/**
 * Offers customers can see.
 */
public interface CouponQueryService {

    /** Coupons usable right now: platform coupons, plus the given store's coupons (storeId may be null). */
    List<PublicCouponResponse> listAvailableCoupons(Long storeId);
}
