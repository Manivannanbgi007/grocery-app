package com.grocery.order.exception;

/**
 * The requested record does not exist. Returned as HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Builds a message like "User not found with id: 42". */
    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
    }
}
