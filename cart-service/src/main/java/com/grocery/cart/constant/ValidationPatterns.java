package com.grocery.cart.constant;

/**
 * Regular expressions used in @Pattern validations.
 */
public final class ValidationPatterns {

    /** 4-20 letters or digits, e.g. WELCOME50. Stored in upper case. */
    public static final String COUPON_CODE = "^[A-Za-z0-9]{4,20}$";

    private ValidationPatterns() {
    }
}
