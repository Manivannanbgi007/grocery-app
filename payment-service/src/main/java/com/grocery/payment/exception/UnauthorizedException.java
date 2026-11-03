package com.grocery.payment.exception;

/**
 * The caller is not logged in, or the credentials are wrong. Returned as HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
