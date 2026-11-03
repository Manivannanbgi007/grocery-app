package com.grocery.gateway.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Settings under "app.cors" in application.yml.
 *
 * @param allowedOrigins web apps (scheme + host + port) allowed to call the API from a browser,
 *                       for example the React frontend at http://localhost:5173
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
