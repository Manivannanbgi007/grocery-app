package com.grocery.product.constant;

/**
 * Regular expressions used in request validation (@Pattern).
 */
public final class ValidationPatterns {

    /** Vendor's own product code: letters, digits and dashes, e.g. FM-VEG-001. */
    public static final String SKU = "^[A-Za-z0-9-]{2,40}$";

    public static final String SKU_MESSAGE = "must be 2-40 letters, digits or dashes";

    private ValidationPatterns() {
    }
}
