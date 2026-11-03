package com.grocery.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One-time token created by "forgot password". It can be used once, before it expires; a newer token, a reset or a
 * password change makes it unusable too. Only its SHA-256 hash is stored (like a refresh token): the token itself is
 * in the user's email only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Sha256.hex(token): 64 hex characters */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    // ----- business rules -----

    public boolean isUsable(Instant now) {
        return !used && now.isBefore(expiresAt);
    }

    public void markUsed() {
        this.used = true;
    }
}
