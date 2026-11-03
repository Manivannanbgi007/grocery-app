package com.grocery.auth.service;

import com.grocery.auth.dto.request.ChangePasswordRequest;
import com.grocery.auth.dto.request.ForgotPasswordRequest;
import com.grocery.auth.dto.request.LoginRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.request.ResetPasswordRequest;
import com.grocery.auth.dto.response.ForgotPasswordResponse;
import com.grocery.auth.dto.response.UserResponse;

/**
 * Account actions a user performs for themselves: register, log in, manage their password.
 */
public interface AuthService {

    /**
     * Creates an ACTIVE customer, vendor or delivery partner and logs them in (access token + a new session).
     *
     * @param currentRefreshToken the refresh cookie this browser still sends, if any: that old session ends
     */
    AuthTokens register(RegisterRequest request, String currentRefreshToken);

    /** Checks the credentials: an access token and a new session (refresh token). */
    AuthTokens login(LoginRequest request, String currentRefreshToken);

    /**
     * A new access token for a refresh token, which is replaced at the same time (rotation).
     *
     * @throws com.grocery.auth.exception.SessionEndedException the session cannot continue: log in again
     */
    AuthTokens refresh(String refreshToken);

    /** Ends the session of this refresh token. */
    void logout(String refreshToken);

    UserResponse getCurrentUser(Long userId);

    /** Changes the password and ends every other session of the user (the one of currentRefreshToken stays). */
    void changePassword(Long userId, ChangePasswordRequest request, String currentRefreshToken);

    /**
     * Creates a one-time reset token when the email is registered.
     *
     * @return the token only when the dev-only expose flag is on, otherwise null
     */
    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
