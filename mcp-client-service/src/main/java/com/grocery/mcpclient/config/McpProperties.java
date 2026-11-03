package com.grocery.mcpclient.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under "app.mcp" in application.yml (the URL of mcp-server-service is under spring.ai.mcp.client).
 *
 * @param apiKey         the shared secret sent in the X-MCP-API-Key header; the same value as in mcp-server-service
 * @param connectTimeout how long to wait for a connection to mcp-server-service
 */
@Validated
@ConfigurationProperties(prefix = "app.mcp")
public record McpProperties(
        @NotBlank(message = MISSING_KEY) @Size(min = 32, message = MISSING_KEY) String apiKey,
        @NotNull Duration connectTimeout) {

    private static final String MISSING_KEY =
            "set the environment variable MCP_API_KEY (at least 32 characters, the same value as in mcp-server-service)";
}
