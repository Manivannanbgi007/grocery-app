package com.grocery.delivery.exception;

/**
 * The request is well-formed but breaks a business rule
 * (for example "current password is incorrect"). Returned as HTTP 400.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
