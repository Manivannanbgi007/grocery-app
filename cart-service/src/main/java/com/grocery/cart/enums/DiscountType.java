package com.grocery.cart.enums;

/**
 * How a coupon reduces the price.
 */
public enum DiscountType {
    /** discountValue percent of the items total, optionally capped by maxDiscountAmount */
    PERCENTAGE,
    /** a fixed amount, e.g. 50.00 off */
    FLAT
}
