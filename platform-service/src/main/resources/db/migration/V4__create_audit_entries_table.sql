-- The audit log: every domain event of the platform, and every change made in this service.
CREATE TABLE audit_entries (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(100)  NOT NULL COMMENT 'eventId of the Kafka event, or a new UUID for our own changes',
    event_type    VARCHAR(100)  NOT NULL COMMENT 'e.g. OrderCancelled, FeatureFlagChanged',
    source        VARCHAR(60)   NOT NULL COMMENT 'The Kafka topic, or platform-service',
    aggregate_id  VARCHAR(60)   NULL     COMMENT 'Kafka message key: order id, store id ...',
    user_id       BIGINT        NULL     COMMENT 'The user the entry is about, or who made the change',
    store_id      BIGINT        NULL,
    details       TEXT          NOT NULL COMMENT 'The whole event as JSON',
    occurred_at   DATETIME(6)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_audit_entries_occurred_at (occurred_at),
    INDEX idx_audit_entries_event_type (event_type),
    INDEX idx_audit_entries_user_id (user_id),
    INDEX idx_audit_entries_aggregate_id (aggregate_id)
);
