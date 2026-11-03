package com.grocery.gateway.constant;

/**
 * HTTP headers shared by all grocery microservices (same names as in every service).
 */
public final class HeaderNames {

    /** Id used to follow one request through the logs of every service. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Id of the logged-in user, taken from the JWT "sub" claim. */
    public static final String USER_ID = "X-User-Id";

    /** Role of the logged-in user, taken from the JWT "role" claim. */
    public static final String USER_ROLE = "X-User-Role";

    /** Vendor's store, taken from the JWT "storeId" claim (vendors only). */
    public static final String STORE_ID = "X-Store-Id";

    private HeaderNames() {
    }
}
