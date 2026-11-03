package com.grocery.platform.dto.response;

import java.time.Instant;

/**
 * One message of a dead-letter topic: the event, why it failed, and whether it was replayed.
 * partition + offset identify it (for a replay).
 *
 * @param failedAt         when it was moved to the dead-letter topic
 * @param key              the Kafka key (order id, product id ...)
 * @param originalOffset   where it was in the original topic
 * @param exceptionClass   the error after the last retry
 * @param payload          the event as JSON text; for a message no consumer could read: its bytes as text
 * @param replayedAt       null = not replayed yet
 * @param replayedByUserId the super admin who replayed it
 */
public record DeadLetterResponse(
        String topic,
        int partition,
        long offset,
        Instant failedAt,
        String key,
        String originalTopic,
        Integer originalPartition,
        Long originalOffset,
        String eventType,
        String eventId,
        String exceptionClass,
        String exceptionMessage,
        String payload,
        Instant replayedAt,
        Long replayedByUserId) {
}
