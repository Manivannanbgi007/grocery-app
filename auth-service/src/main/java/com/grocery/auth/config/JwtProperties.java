package com.grocery.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under "app.jwt" in application.yml.
 * The application refuses to start when a value is invalid (for example a secret that is too short).
 *
 * @param secret              shared HS256 secret; the API gateway must use the same value
 * @param accessTokenLifetime how long an access token is valid (env JWT_ACCESS_TOKEN_LIFETIME, e.g. 15m). Short on
 *                            purpose: an access token cannot be withdrawn, the refresh token gives a new one
 * @param issuer              value of the "iss" claim
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotNull Duration accessTokenLifetime,
        @NotBlank String issuer) {
}
