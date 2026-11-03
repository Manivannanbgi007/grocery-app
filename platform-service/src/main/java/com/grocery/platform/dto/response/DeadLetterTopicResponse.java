package com.grocery.platform.dto.response;

/**
 * A dead-letter topic: where the events of {@code originalTopic} go that failed after all retries.
 *
 * @param messages messages Kafka still keeps (30 days), replayed ones included
 */
public record DeadLetterTopicResponse(
        String topic,
        String originalTopic,
        long messages) {
}
