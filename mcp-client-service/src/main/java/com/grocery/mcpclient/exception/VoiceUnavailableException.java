package com.grocery.mcpclient.exception;

/**
 * The speech server (speaches) is not running, too slow, or not set up (a model missing). Returned as HTTP 503.
 * The message is safe to show to the user; the technical cause is only logged. Typing still works.
 */
public class VoiceUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Voice is not available right now. Please type your question.";

    public VoiceUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
