package com.grocery.delivery.exception;

/**
 * The service cannot take this request right now (for example too many live tracking streams are open).
 * Returned as HTTP 503: the client may try again a little later.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
