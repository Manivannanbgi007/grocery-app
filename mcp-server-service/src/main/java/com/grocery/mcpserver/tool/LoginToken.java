package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.exception.LoginRequiredException;
import io.modelcontextprotocol.common.McpTransportContext;

/**
 * The logged-in user's token, for the tools that read a user's own data (customer, vendor, partner and admin tools).
 *
 * mcp-client-service sends it in the X-User-Token header; McpTransportConfig puts it into the MCP transport context,
 * which Spring AI passes to the tool as a parameter the model never sees. Without a token the tool answers
 * LOGIN_REQUIRED (ToolRunner turns the exception into that status).
 */
final class LoginToken {

    private LoginToken() {
    }

    /**
     * @param notLoggedIn the message for the model when there is no token, e.g. "The vendor is not logged in ..."
     */
    static String of(McpTransportContext context, String notLoggedIn) {
        if (context != null && context.get(McpTransportConfig.USER_TOKEN) instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new LoginRequiredException(notLoggedIn);
    }
}
