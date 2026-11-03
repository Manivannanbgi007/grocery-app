package com.grocery.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One line of the audit log: something that happened on the platform. Never changed after it is written.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use AuditEntry.of(...)
@Entity
@Table(name = "audit_entries")
public class AuditEntry extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 100, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    /** The Kafka topic it came from, or "platform-service" for changes made here. */
    @Column(nullable = false, length = 60, updatable = false)
    private String source;

    @Column(name = "aggregate_id", length = 60, updatable = false)
    private String aggregateId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "store_id", updatable = false)
    private Long storeId;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static AuditEntry of(String eventId, String eventType, String source, String aggregateId, Long userId,
                                Long storeId, String details, Instant occurredAt) {
        AuditEntry entry = new AuditEntry();
        entry.eventId = eventId;
        entry.eventType = eventType;
        entry.source = source;
        entry.aggregateId = aggregateId;
        entry.userId = userId;
        entry.storeId = storeId;
        entry.details = details;
        entry.occurredAt = occurredAt;
        return entry;
    }
}
