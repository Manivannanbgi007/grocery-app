package com.grocery.cart.dto.response;

import java.util.List;

/**
 * @param storeId       store of the items (null for an empty cart)
 * @param couponMessage what happened to the coupon: "you save ..." or why it cannot be used right now
 */
public record CartResponse(
        Long customerUserId,
        Long storeId,
        List<CartItemResponse> items,
        int itemCount,
        String couponCode,
        String couponMessage,
        CartTotalsResponse totals) {
}
