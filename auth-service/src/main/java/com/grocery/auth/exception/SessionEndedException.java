package com.grocery.auth.exception;

/**
 * The refresh token cannot be used: missing, unknown, expired, revoked or reused. Returned as HTTP 401, and the
 * GlobalExceptionHandler also removes the refresh cookie, so the browser stops sending a dead token.
 */
public class SessionEndedException extends UnauthorizedException {

    public SessionEndedException() {
        super("Your session has ended. Please log in again.");
    }
}
