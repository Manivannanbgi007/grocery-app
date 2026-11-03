package com.grocery.mcpclient.dto.response;

/**
 * The words of a spoken question. The client sends them on as a normal message (POST /api/v1/chat/messages), so a
 * spoken question gets the same history, limits and answer check as a typed one.
 *
 * @param text the question, cleaned (can be longer than the 500 characters a message may have)
 */
public record TranscriptionResponse(String text) {
}
