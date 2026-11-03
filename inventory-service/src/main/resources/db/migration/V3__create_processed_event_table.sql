-- Ids of the Kafka events this service has already handled (idempotent consumer).
CREATE TABLE processed_event (
    event_id      VARCHAR(36)  NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
);
