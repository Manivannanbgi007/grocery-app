package com.grocery.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    private final Instant now = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void isUsable_whenNotUsedAndNotExpired() {
        PasswordResetToken token = tokenExpiringAt(now.plus(30, ChronoUnit.MINUTES));

        assertThat(token.isUsable(now)).isTrue();
    }

    @Test
    void isNotUsable_afterExpiry() {
        PasswordResetToken token = tokenExpiringAt(now.minus(1, ChronoUnit.MINUTES));

        assertThat(token.isUsable(now)).isFalse();
    }

    @Test
    void isNotUsable_afterBeingUsedOnce() {
        PasswordResetToken token = tokenExpiringAt(now.plus(30, ChronoUnit.MINUTES));

        token.markUsed();

        assertThat(token.isUsable(now)).isFalse();
    }

    private PasswordResetToken tokenExpiringAt(Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(expiresAt);
        return token;
    }
}
