package com.grocery.mcpserver.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.security" in application.yml.
 *
 * @param mcpApiKey the shared secret that mcp-client-service sends in the X-MCP-API-Key header.
 *                  At least 32 characters, so it cannot be guessed; the service does not start without it.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record McpSecurityProperties(
        @NotBlank(message = MISSING_KEY) @Size(min = 32, message = MISSING_KEY) String mcpApiKey) {

    private static final String MISSING_KEY =
            "set the environment variable MCP_API_KEY (at least 32 characters, the same value as in mcp-client-service)";
}
