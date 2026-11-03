package com.grocery.auth.security;

import com.grocery.auth.config.SessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The cookie that carries the refresh token to the browser:
 *
 *   refresh_token=x8Fq...; Path=/api/v1/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict
 *
 *   HttpOnly         JavaScript cannot read it: a script injected into the page (XSS) cannot steal it
 *   Secure           only sent over HTTPS (browsers make an exception for http://localhost)
 *   SameSite=Strict  not sent with requests started by another site: protects /refresh and /logout against CSRF
 *   Path             only sent to /api/v1/auth/... (refresh, logout, password change), not with every API call
 *   Max-Age          until the session's current token expires
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final SessionProperties sessionProperties;
    private final Clock clock;

    /** Set-Cookie value that stores the token until it expires. */
    public String create(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        return cookie(token, maxAge.isNegative() ? Duration.ZERO : maxAge);
    }

    /** Set-Cookie value that removes the cookie from the browser (logout, ended session). */
    public String clear() {
        return cookie("", Duration.ZERO);
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(sessionProperties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
