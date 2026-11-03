package com.grocery.notification.exception;

/**
 * The password-reset token of a PasswordResetRequested event cannot be read: another key than auth-service's
 * (PASSWORD_RESET_EVENT_KEY), a changed text, or no token at all. Retrying cannot help, so the event goes straight to
 * the dead-letter topic (KafkaConsumerConfig); fix the key, then replay it from there. The message never contains the token.
 */
public class UnreadableResetTokenException extends RuntimeException {

    public UnreadableResetTokenException(String message) {
        super(message);
    }

    public UnreadableResetTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
