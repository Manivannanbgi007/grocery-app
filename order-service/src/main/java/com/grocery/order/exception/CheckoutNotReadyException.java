package com.grocery.order.exception;

import lombok.Getter;

import java.util.List;

/**
 * The cart cannot become an order yet (out of stock, coupon no longer valid, below the minimum ...).
 * Returned as HTTP 409 with every problem in "errors", so the customer can fix them all at once.
 */
@Getter
public class CheckoutNotReadyException extends RuntimeException {

    private final List<String> problems;

    public CheckoutNotReadyException(List<String> problems) {
        super("Your cart cannot be ordered yet. Please fix the problems listed and try again.");
        this.problems = problems;
    }
}
