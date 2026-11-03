package com.grocery.mcpserver.constant;

/**
 * HTTP headers used by this service.
 */
public final class HeaderNames {

    /** Id used to follow one request through the logs of every service. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** The shared secret mcp-client-service sends on every MCP request (see ApiKeyAuthenticationFilter). */
    public static final String MCP_API_KEY = "X-MCP-API-Key";

    /**
     * The logged-in user's access token (JWT), sent by mcp-client-service for every logged-in user (customer, vendor,
     * delivery partner, admin). The tools for a user's own data pass it on to the gateway, so they can only ever read
     * what that user may see on the website. Never logged.
     */
    public static final String USER_TOKEN = "X-User-Token";

    private HeaderNames() {
    }
}
