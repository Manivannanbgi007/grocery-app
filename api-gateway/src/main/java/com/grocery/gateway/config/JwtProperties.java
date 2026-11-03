package com.grocery.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.jwt" in application.yml.
 *
 * @param secret shared HS256 secret - MUST be the same value as auth-service's app.jwt.secret
 * @param issuer expected "iss" claim - tokens from anyone else are rejected
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotBlank String issuer) {
}
