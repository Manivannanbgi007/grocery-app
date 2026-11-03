-- Transactional outbox: events are saved here in the SAME transaction as the business change.
-- Debezium reads new rows from the MySQL binlog and publishes them to Kafka
-- (see debezium/inventory-outbox-connector.json). Rows are only inserted, never updated.
CREATE TABLE outbox_event (
    id              VARCHAR(36)  NOT NULL COMMENT 'Event id (UUID), sent as the "id" Kafka header',
    aggregate_type  VARCHAR(50)  NOT NULL COMMENT 'Decides the topic: inventory -> grocery.inventory.events',
    aggregate_id    VARCHAR(50)  NOT NULL COMMENT 'Kafka message key',
    event_type      VARCHAR(100) NOT NULL COMMENT 'Avro record name, sent as the "eventType" Kafka header',
    payload         MEDIUMBLOB   NOT NULL COMMENT 'Avro bytes (Confluent format: magic byte + schema id + data)',
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_event_created_at (created_at)
);
