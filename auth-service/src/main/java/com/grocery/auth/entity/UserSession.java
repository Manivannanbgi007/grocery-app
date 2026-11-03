package com.grocery.auth.entity;

import com.grocery.auth.enums.SessionEndReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * One login session: one login on one browser or device. It holds the hash of the CURRENT refresh token; every
 * refresh replaces the token ("rotation") and keeps the previous hash, so a stolen old token can be recognised.
 *
 *   expires_at          the current token expires (last refresh + 7 days): an active user stays logged in
 *   session_expires_at  the session ends at the latest (login + 30 days), however active the user is
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use UserSession.start(...)
@Entity
@Table(name = "user_sessions")
public class UserSession extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "session_expires_at", nullable = false, updatable = false)
    private Instant sessionExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private SessionEndReason revokedReason;

    public static UserSession start(Long userId, String tokenHash, Instant now, Duration tokenLifetime, Duration maxLifetime) {
        UserSession session = new UserSession();
        session.userId = userId;
        session.tokenHash = tokenHash;
        session.sessionExpiresAt = now.plus(maxLifetime);
        session.expiresAt = earliest(now.plus(tokenLifetime), session.sessionExpiresAt);
        return session;
    }

    /** A new refresh token replaces the current one; the old hash is kept to recognise it if it comes back. */
    public void rotate(String newTokenHash, Instant now, Duration tokenLifetime) {
        this.previousTokenHash = this.tokenHash;
        this.tokenHash = newTokenHash;
        this.rotatedAt = now;
        this.expiresAt = earliest(now.plus(tokenLifetime), sessionExpiresAt);
    }

    public void revoke(SessionEndReason reason, Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** The current token is too old, or the session reached its maximum age. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** The token was replaced so recently that the old one may still be on its way (two tabs refreshing at once). */
    public boolean wasRotatedWithin(Duration grace, Instant now) {
        return rotatedAt != null && !now.isAfter(rotatedAt.plus(grace));
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
