package com.grocery.cart.pricing;

import java.math.BigDecimal;

/**
 * The result of checking the coupon in a cart.
 *
 * @param code     the coupon code in the cart (null = no coupon)
 * @param discount the discount it gives right now (0 when it cannot be used)
 * @param problem  why it cannot be used right now (null = usable)
 */
public record AppliedCoupon(String code, BigDecimal discount, String problem) {

    public static AppliedCoupon none() {
        return new AppliedCoupon(null, BigDecimal.ZERO, null);
    }

    public boolean isPresent() {
        return code != null;
    }

    public boolean isUsable() {
        return isPresent() && problem == null;
    }

    /** A short text for the cart page. */
    public String message() {
        if (!isPresent()) {
            return null;
        }
        return isUsable() ? "Coupon " + code + " applied: you save " + discount : problem;
    }
}
