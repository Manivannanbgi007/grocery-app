package com.grocery.mcpclient.exception;

/**
 * The requested record does not exist (or belongs to someone else). Returned as HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
