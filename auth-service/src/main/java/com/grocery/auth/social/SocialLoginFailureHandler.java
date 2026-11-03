package com.grocery.auth.social;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.util.Set;

/**
 * The provider's answer is not a login: the user cancelled, or the answer was invalid (unknown or reused state, a bad
 * ID token, an error from the provider). The browser goes back to the shop with a fixed reason, never the provider's
 * text: /login/social#error=cancelled or #error=failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialLoginFailureHandler implements AuthenticationFailureHandler {

    /** What the providers answer when the user clicks "Cancel" (Apple has its own word for it). */
    private static final Set<String> CANCELLED = Set.of("access_denied", "user_cancelled_authorize");

    private final SocialLoginProperties properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "unknown";
        String reason = CANCELLED.contains(errorCode) ? "cancelled" : "failed";
        if (reason.equals("cancelled")) {
            log.info("Social login cancelled by the user");
        } else {
            log.warn("Social login failed: {} ({})", errorCode, exception.getMessage());
        }
        response.sendRedirect(properties.publicUrl() + "/login/social#error=" + reason);
    }
}
