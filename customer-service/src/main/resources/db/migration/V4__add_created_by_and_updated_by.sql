-- Who added and who last changed each row (see BaseEntity): the role and id of the logged-in user,
-- for example 'CUSTOMER:4', or 'SYSTEM' for changes made by Kafka events, scheduled jobs and public endpoints.
-- Filled by Spring Data auditing. Empty (NULL) for rows that existed before this migration.

ALTER TABLE customers
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;

ALTER TABLE addresses
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;
