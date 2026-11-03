package com.grocery.mcpclient.exception;

/**
 * The language model (Ollama) or the tools (mcp-server-service) did not answer in time. Returned as HTTP 503.
 * The message is safe to show to the user (shop or panel); the technical cause is only logged.
 */
public class AssistantUnavailableException extends RuntimeException {

    public static final String MESSAGE =
            "The assistant is not available right now. Please try again in a few minutes.";

    public AssistantUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
