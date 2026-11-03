package com.grocery.auth.dto.response;

import java.time.Instant;

/**
 * DEV ONLY: only returned when app.password-reset.expose-token-in-response=true
 * (local profile), so the reset flow can be tested without an email server.
 */
public record ForgotPasswordResponse(String resetToken, Instant expiresAt) {
}
