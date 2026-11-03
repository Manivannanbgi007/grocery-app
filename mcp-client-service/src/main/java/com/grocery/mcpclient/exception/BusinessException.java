package com.grocery.mcpclient.exception;

/**
 * The request is well-formed but cannot be processed (for example a message with nothing but control characters).
 * Returned as HTTP 400.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
