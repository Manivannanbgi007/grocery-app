package com.grocery.mcpserver.exception;

/**
 * A customer tool was called without a valid customer login (no token, an expired token, or not a customer account).
 * The tools turn it into the LOGIN_REQUIRED status; the message is safe to show to the customer.
 */
public class LoginRequiredException extends RuntimeException {

    public LoginRequiredException(String message) {
        super(message);
    }
}
