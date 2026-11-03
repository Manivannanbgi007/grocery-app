package com.grocery.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned after login or registration.
 * The client sends the token on every later call:  Authorization: Bearer &lt;accessToken&gt;
 */
public record AuthResponse(
        @Schema(description = "JWT access token") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the token expires", example = "3600") long expiresInSeconds,
        UserResponse user) {
}
