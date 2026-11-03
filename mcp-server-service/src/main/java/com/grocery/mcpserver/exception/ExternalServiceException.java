package com.grocery.mcpserver.exception;

/**
 * The API gateway or a service behind it did not answer (down, timeout, error).
 * The tools turn it into a SERVICE_UNAVAILABLE result, so the model can tell the customer to try again later.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
