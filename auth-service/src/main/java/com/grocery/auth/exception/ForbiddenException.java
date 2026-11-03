package com.grocery.auth.exception;

/**
 * The caller is known but not allowed to do this (for example a disabled account). Returned as HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
