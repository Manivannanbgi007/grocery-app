package com.grocery.payment.exception;

/**
 * The record already exists (for example an email that is already registered). Returned as HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
