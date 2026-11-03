package com.grocery.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to the exchange of the one-time code.
 *   LOGGED_IN          auth is set (like after POST /login; the refresh token is in the cookie)
 *   SIGNUP_REQUIRED    no account yet: fullName and email pre-fill the sign-up form (emailLocked: the provider verified it)
 *   PASSWORD_REQUIRED  an account with this email exists: its password connects the provider account once
 */
public record SocialSessionResponse(
        @Schema(example = "LOGGED_IN") String status,
        @Schema(example = "google") String provider,
        AuthResponse auth,
        String fullName,
        String email,
        boolean emailLocked) {
}
