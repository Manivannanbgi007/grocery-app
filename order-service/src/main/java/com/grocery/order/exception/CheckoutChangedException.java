package com.grocery.order.exception;

/**
 * The customer confirmed a summary of their cart (the chat's Confirm button), but the cart or its prices changed since:
 * placing the order now would place something they did not see. Returned as HTTP 409.
 */
public class CheckoutChangedException extends RuntimeException {

    public CheckoutChangedException() {
        super("Your cart or its prices changed since the summary. Please check the new total.");
    }
}
