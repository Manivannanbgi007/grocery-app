package com.grocery.auth.social;

import com.grocery.auth.config.SessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The cookie that ties a social login attempt to the browser that started it:
 *
 *   social_flow=x8Fq...; Path=/api/v1/auth/social; Max-Age=...; HttpOnly; Secure; SameSite=Strict
 *
 * It is set when the user clicks "Continue with Google", and the steps after the provider's answer (exchange the
 * one-time code, sign up, enter the password to connect) only work with it. Someone who tricks a victim's browser into
 * using the attacker's code (to log the victim into the attacker's account) cannot set this cookie for our site.
 */
@Component
@RequiredArgsConstructor
public class SocialFlowCookies {

    public static final String NAME = "social_flow";
    private static final String PATH = "/api/v1/auth/social";

    private final SessionProperties sessionProperties;

    public String create(String value, Duration maxAge) {
        return cookie(value, maxAge);
    }

    /** Removes the cookie once the attempt is finished. */
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
