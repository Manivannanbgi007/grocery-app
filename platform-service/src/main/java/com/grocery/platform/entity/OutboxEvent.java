package com.grocery.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the transactional outbox = one event waiting to be published.
 *
 * Rows are only ever inserted (never updated), so this entity does not extend BaseEntity.
 * Debezium reads each new row from the MySQL binlog and publishes the payload to Kafka.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    /** The event id (UUID) - Debezium sends it as the "id" Kafka header. */
    @Id
    @Column(length = 36)
    private String id;

    /** Decides the topic: "user" -> grocery.user.events. */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /** Id of the changed record - becomes the Kafka message key, so its events stay in order. */
    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    /** Avro record name, e.g. UserRegistered - Debezium sends it as the "eventType" Kafka header. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** The Avro-encoded event (Confluent wire format: magic byte + schema id + data). */
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * The trace of the request or event that created this event, as a W3C "traceparent" value
     * (00-&lt;trace id&gt;-&lt;span id&gt;-01); null outside a trace. Debezium sends it as the "traceparent" Kafka header,
     * so the consumer continues the same trace (docs/observability.md, "Traces").
     */
    @Column(length = 55)
    private String traceparent;
}
