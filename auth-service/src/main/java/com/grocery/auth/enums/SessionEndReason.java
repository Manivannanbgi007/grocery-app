package com.grocery.auth.enums;

/**
 * Why a login session was ended before it expired (user_sessions.revoked_reason).
 */
public enum SessionEndReason {
    /** The user logged out. */
    LOGOUT,
    /** An old refresh token came back after it had been replaced: probably stolen, so the session is ended. */
    REUSE_DETECTED,
    /** The user changed their password: every other session ends. */
    PASSWORD_CHANGED,
    /** The password was reset with a reset token: every session ends. */
    PASSWORD_RESET,
    /** An admin disabled the account. */
    USER_DISABLED,
    /** A super admin changed the user's role. */
    ROLE_CHANGED,
    /** The same browser logged in again: its old session is replaced by the new one. */
    SUPERSEDED
}
