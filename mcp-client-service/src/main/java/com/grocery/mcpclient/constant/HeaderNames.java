package com.grocery.mcpclient.constant;

/**
 * HTTP header names used by this service.
 */
public final class HeaderNames {

    /** One id per request, passed from service to service so all log lines of one request can be found. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Added by the API gateway after it validated the JWT (never trusted from the browser). */
    public static final String USER_ID = "X-User-Id";

    /** Added by the API gateway after it validated the JWT. */
    public static final String USER_ROLE = "X-User-Role";

    /** The shared secret mcp-server-service expects from us. */
    public static final String MCP_API_KEY = "X-MCP-API-Key";

    /**
     * The logged-in user's token, for mcp-server-service's tools for the user's own data (a customer's cart and
     * orders, a vendor's store, a partner's deliveries, the admins' numbers).
     */
    public static final String USER_TOKEN = "X-User-Token";

    private HeaderNames() {
    }
}
