-- Who added and who last changed each row (see BaseEntity): the role and id of the logged-in user,
-- for example 'VENDOR:12' or 'ADMIN:1', or 'SYSTEM' for changes made by Kafka events, scheduled jobs and public endpoints.
-- Filled by Spring Data auditing. Empty (NULL) for rows that existed before this migration.
-- products and categories are also read by Debezium (product-cdc-connector): the two columns appear in the
-- change events as optional fields, and product-read-service ignores them (it reads the fields it knows by name).

ALTER TABLE categories
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;

ALTER TABLE products
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;

ALTER TABLE product_reviews
    ADD COLUMN created_by VARCHAR(50) NULL AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(50) NULL AFTER created_by;
