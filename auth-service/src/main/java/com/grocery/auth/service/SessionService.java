package com.grocery.auth.service;

import com.grocery.auth.entity.User;
import com.grocery.auth.enums.SessionEndReason;

import java.time.Instant;

/**
 * Login sessions and their refresh tokens (table user_sessions, docs/authentication.md).
 */
public interface SessionService {

    /** A new refresh token for the cookie, and until when it is valid. */
    record IssuedToken(String token, Instant expiresAt) {
    }

    /** The user of a refreshed session, and the new refresh token (null: keep the cookie, see "reuse grace"). */
    record Refreshed(User user, IssuedToken newToken) {
    }

    /** Starts a session for a user who just logged in or registered. */
    IssuedToken start(User user);

    /**
     * Exchanges a refresh token for a new one (rotation).
     *
     * @throws com.grocery.auth.exception.SessionEndedException the token is missing, unknown, expired, revoked or was
     *                                                          used again after it had been replaced (the session is
     *                                                          then ended: probably stolen)
     */
    Refreshed refresh(String token);

    /** Ends the session of this token (logout); nothing happens for an unknown token. */
    void end(String token, SessionEndReason reason);

    /** Ends every session of the user (password reset, account disabled, role changed). */
    void endAll(Long userId, SessionEndReason reason);

    /** Ends every session of the user except the one of keepToken (password change: the user stays logged in here). */
    void endAllExcept(Long userId, String keepToken, SessionEndReason reason);
}
