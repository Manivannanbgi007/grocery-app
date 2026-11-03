package com.grocery.store.constant;

/**
 * Regular expressions used in request validation (@Pattern).
 */
public final class ValidationPatterns {

    /** 10-digit Indian mobile number starting with 6, 7, 8 or 9. */
    public static final String INDIAN_MOBILE = "^[6-9]\\d{9}$";

    /** 6-digit Indian PIN code (does not start with 0). */
    public static final String PINCODE = "^[1-9]\\d{5}$";

    public static final String INDIAN_MOBILE_MESSAGE = "must be a valid 10-digit Indian mobile number";
    public static final String PINCODE_MESSAGE = "must be a valid 6-digit PIN code";

    private ValidationPatterns() {
    }
}
