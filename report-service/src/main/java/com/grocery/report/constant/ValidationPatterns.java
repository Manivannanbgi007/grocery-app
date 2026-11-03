package com.grocery.report.constant;

/**
 * Regular expressions used in request validation (@Pattern).
 */
public final class ValidationPatterns {

    /** 10-digit Indian mobile number starting with 6, 7, 8 or 9. */
    public static final String INDIAN_MOBILE = "^[6-9]\\d{9}$";

    /** At least one letter and one digit (length is checked separately with @Size). */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    public static final String INDIAN_MOBILE_MESSAGE = "must be a valid 10-digit Indian mobile number";
    public static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";

    private ValidationPatterns() {
    }
}
