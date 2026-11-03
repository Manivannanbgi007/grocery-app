package com.grocery.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A provider account (Google, Microsoft ...) linked to a user: logging in with it logs in that user. Found by the
 * provider's stable id of the person ("sub"), never by the email: an email can change or be given to someone else.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "social_logins")
public class SocialLogin extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    /** The email the provider gave when it was linked (for support only; never used to find the user). */
    @Column(length = 150)
    private String email;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public static SocialLogin link(User user, String provider, String providerUserId, String email, Instant now) {
        SocialLogin socialLogin = new SocialLogin();
        socialLogin.user = user;
        socialLogin.provider = provider;
        socialLogin.providerUserId = providerUserId;
        socialLogin.email = email;
        socialLogin.lastUsedAt = now;
        return socialLogin;
    }

    public void used(Instant now) {
        this.lastUsedAt = now;
    }
}
