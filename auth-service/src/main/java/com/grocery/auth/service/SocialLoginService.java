package com.grocery.auth.service;

import com.grocery.auth.dto.request.SocialLinkRequest;
import com.grocery.auth.dto.request.SocialSignupRequest;
import com.grocery.auth.social.SocialProfile;

/**
 * Social login after the provider's answer (docs/social-login.md). The provider only says who the person is; logging
 * in, signing up and the session work exactly like with a password (LoginSessions, AuthService.register/login).
 */
public interface SocialLoginService {

    /** The provider's answer was valid: keep who it is in the attempt; returns the browser's one-time code (60 s). */
    String acceptProfile(Long flowId, SocialProfile profile);

    /**
     * The shop page exchanges the one-time code (only in the browser that started the attempt). Logged in when the
     * provider account is linked, or can be linked automatically; otherwise the user signs up, or enters the password of
     * the existing account with the same email.
     */
    SocialLoginResult redeem(String code, String browserToken, String currentRefreshToken);

    /** A new account for this provider account: the registration with a password, minus the password. */
    AuthTokens signUp(String browserToken, SocialSignupRequest request, String currentRefreshToken);

    /** Links the provider account to the existing account with the same email, after its password was entered once. */
    AuthTokens link(String browserToken, SocialLinkRequest request, String currentRefreshToken);

    /** What redeem found. tokens only when LOGGED_IN; the other fields for the sign-up or password form. */
    record SocialLoginResult(Status status, String provider, AuthTokens tokens, String fullName, String email, boolean emailLocked) {

        public enum Status { LOGGED_IN, SIGNUP_REQUIRED, PASSWORD_REQUIRED }

        public static SocialLoginResult loggedIn(String provider, AuthTokens tokens) {
            return new SocialLoginResult(Status.LOGGED_IN, provider, tokens, null, null, false);
        }

        public static SocialLoginResult signupRequired(SocialProfile profile) {
            return new SocialLoginResult(Status.SIGNUP_REQUIRED, profile.provider(), null, profile.fullName(), profile.email(),
                    profile.emailVerified());
        }

        public static SocialLoginResult passwordRequired(String provider, String email) {
            return new SocialLoginResult(Status.PASSWORD_REQUIRED, provider, null, null, email, true);
        }
    }
}
