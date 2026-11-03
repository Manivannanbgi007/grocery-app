package com.grocery.mcpserver.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.gateway" in application.yml: where the API gateway is.
 * Every tool reads its data through the gateway, like the website does (public endpoints, same rules and limits).
 *
 * @param url base URL of the API gateway, e.g. http://localhost:8080 (in Kubernetes: http://api-gateway:8080)
 */
@Validated
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(@NotBlank String url) {
}
