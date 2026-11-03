package com.grocery.store.constant;

/**
 * HTTP headers shared by all grocery microservices.
 *
 * The API gateway validates the JWT and then adds the X-User-* headers,
 * so services never have to parse tokens themselves.
 */
public final class HeaderNames {

    /** Id used to follow one request through the logs of every service. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Id of the logged-in user (set by the API gateway). */
    public static final String USER_ID = "X-User-Id";

    /** Role of the logged-in user, e.g. CUSTOMER (set by the API gateway). */
    public static final String USER_ROLE = "X-User-Role";

    /** Store of the logged-in vendor (set by the API gateway, vendors only). */
    public static final String STORE_ID = "X-Store-Id";

    private HeaderNames() {
    }
}
