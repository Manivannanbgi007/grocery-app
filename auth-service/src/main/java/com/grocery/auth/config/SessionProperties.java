package com.grocery.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the login sessions (refresh tokens) under "app.session" in application.yml (docs/authentication.md).
 *
 * @param refreshTokenLifetime how long a refresh token is valid; every refresh gives a new one, so an active user stays
 *                             logged in (env REFRESH_TOKEN_LIFETIME, e.g. 7d)
 * @param maxLifetime          a session ends at the latest this long after the login, however active the user is
 *                             (env SESSION_MAX_LIFETIME, e.g. 30d)
 * @param reuseGrace           a token replaced less than this long ago is still accepted once, without a new refresh
 *                             token: two tabs that refresh at the same moment (e.g. 10s)
 * @param cookieSecure         the refresh cookie is only sent over HTTPS (browsers also accept it on http://localhost);
 *                             false only for a test setup without HTTPS on another host (env REFRESH_COOKIE_SECURE)
 */
@Validated
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        @NotNull Duration refreshTokenLifetime,
        @NotNull Duration maxLifetime,
        @NotNull Duration reuseGrace,
        boolean cookieSecure) {
}
