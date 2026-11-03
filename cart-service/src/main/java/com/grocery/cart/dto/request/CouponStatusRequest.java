package com.grocery.cart.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * @param active false = customers can no longer use the coupon
 */
public record CouponStatusRequest(@NotNull Boolean active) {
}
