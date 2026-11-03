package com.grocery.platform.dto.response;

/**
 * @param replayed how many messages were sent back to their topic
 */
public record DeadLetterReplayResponse(
        String topic,
        int replayed) {
}
