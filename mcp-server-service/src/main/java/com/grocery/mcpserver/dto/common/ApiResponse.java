package com.grocery.mcpserver.dto.common;

import java.time.Instant;
import java.util.List;

/**
 * The standard error body of every grocery microservice (here only for 401 / 403 answers;
 * MCP requests and answers use the MCP protocol's own JSON-RPC format).
 *
 * <pre>
 * { "success": false, "message": "...", "data": null, "errors": null, "timestamp": "...", "path": "/mcp" }
 * </pre>
 */
public record ApiResponse<T>(boolean success, String message, T data, List<String> errors, Instant timestamp, String path) {

    public static ApiResponse<Void> error(String message, String path) {
        return new ApiResponse<>(false, message, null, null, Instant.now(), path);
    }
}
