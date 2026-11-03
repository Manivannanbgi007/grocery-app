package com.grocery.mcpclient.exception;

/**
 * All answer slots (app.chat.max-concurrent-chats) or voice slots (app.voice.max-concurrent) stayed busy for their
 * busy-wait. Returned as HTTP 503 with a Retry-After header.
 */
public class AssistantBusyException extends RuntimeException {

    public AssistantBusyException() {
        this("The assistant is busy with other questions. Please try again in a moment.");
    }

    public AssistantBusyException(String message) {
        super(message);
    }
}
