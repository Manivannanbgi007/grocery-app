package com.grocery.platform.dto.response;

import java.time.Instant;

/**
 * @param details the whole event as JSON text
 */
public record AuditEntryResponse(
        Long id,
        String eventId,
        String eventType,
        String source,
        String aggregateId,
        Long userId,
        Long storeId,
        String details,
        Instant occurredAt) {
}
