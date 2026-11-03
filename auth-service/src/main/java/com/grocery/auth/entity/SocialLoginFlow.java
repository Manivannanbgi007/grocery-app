package com.grocery.auth.entity;

import com.grocery.auth.social.SocialProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One social login attempt, from the click on "Continue with Google" until the user is logged in or it expires
 * (V10__social_login.sql explains the stages). The secrets in it are stored as SHA-256 hashes: the OAuth "state", the
 * cookie that ties it to the browser, and the one-time code handed to the browser after the provider's answer.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "social_login_flows")
public class SocialLoginFlow {

    public enum Stage { STARTED, CALLBACK, AUTHENTICATED, REDEEMED, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "browser_hash", nullable = false, length = 64)
    private String browserHash;

    @Column(nullable = false, length = 20)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Stage stage;

    /** Spring Security's authorization request (state, nonce, PKCE verifier) as JSON, until the provider answers. */
    @Column(name = "authorization_request", columnDefinition = "TEXT")
    private String authorizationRequest;

    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(length = 150)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SocialLoginFlow start(String stateHash, String browserHash, String provider, String authorizationRequest,
                                        Instant now, Instant expiresAt) {
        SocialLoginFlow flow = new SocialLoginFlow();
        flow.stateHash = stateHash;
        flow.browserHash = browserHash;
        flow.provider = provider;
        flow.stage = Stage.STARTED;
        flow.authorizationRequest = authorizationRequest;
        flow.createdAt = now;
        flow.expiresAt = expiresAt;
        return flow;
    }

    /** The provider's answer was valid: keep who it is, and the hash of the one-time code for the browser. */
    public void authenticated(SocialProfile profile, String codeHash, Instant expiresAt) {
        this.stage = Stage.AUTHENTICATED;
        this.authorizationRequest = null;   // the nonce and the PKCE verifier are not needed any more
        this.providerUserId = profile.providerUserId();
        this.email = profile.email();
        this.emailVerified = profile.emailVerified();
        this.fullName = profile.fullName();
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public void done() {
        this.stage = Stage.DONE;
    }

    /** Who the provider said the person is (kept since the provider's answer). */
    public SocialProfile profile() {
        return new SocialProfile(provider, providerUserId, email, emailVerified, fullName);
    }
}
