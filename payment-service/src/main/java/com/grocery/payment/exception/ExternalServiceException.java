package com.grocery.payment.exception;

/**
 * Another service we depend on did not answer (down, timeout, error). Returned as HTTP 503.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
