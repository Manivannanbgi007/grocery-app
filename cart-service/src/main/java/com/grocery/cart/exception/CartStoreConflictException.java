package com.grocery.cart.exception;

/**
 * The cart already holds items from another store (one store per order). Returned as HTTP 409.
 */
public class CartStoreConflictException extends RuntimeException {

    public CartStoreConflictException(String message) {
        super(message);
    }
}
