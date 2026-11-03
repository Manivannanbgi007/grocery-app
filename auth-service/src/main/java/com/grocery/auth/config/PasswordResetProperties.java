package com.grocery.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.password-reset" in application.yml.
 *
 * @param tokenValidityMinutes   how long a reset token can be used
 * @param exposeTokenInResponse  DEV ONLY - return the token in the API response (local profile only)
 * @param eventKey               the AES-256 key (32 bytes, Base64) that encrypts the token in the PasswordResetRequested
 *                               event (ResetTokenCipher). notification-service has the same key to read it.
 */
@Validated
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
        @Positive long tokenValidityMinutes,
        boolean exposeTokenInResponse,
        @NotBlank String eventKey) {
}
