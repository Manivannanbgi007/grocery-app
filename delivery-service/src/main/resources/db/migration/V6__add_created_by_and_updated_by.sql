-- Who added and who last changed each row (see BaseEntity): the role and id of the logged-in user,
-- for example 'DELIVERY_PARTNER:5' or 'ADMIN:1', or 'SYSTEM' for changes made by Kafka events, scheduled jobs and public endpoints.
-- Filled by Spring Data auditing. Empty (NULL) for rows that existed before this migration.

ALTER TABLE delivery_zones
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;

ALTER TABLE delivery_partners
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;

ALTER TABLE deliveries
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;
