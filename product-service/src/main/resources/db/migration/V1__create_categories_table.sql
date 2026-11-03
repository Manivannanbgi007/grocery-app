-- Global product categories, managed by admins.
-- Debezium copies every change of this table to Kafka (cdc.product_db.categories).
CREATE TABLE categories (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(60)   NOT NULL,
    slug           VARCHAR(70)   NOT NULL COMMENT 'URL-friendly name, e.g. dairy-eggs',
    description    VARCHAR(255)  NULL,
    image_url      VARCHAR(500)  NULL,
    display_order  INT           NOT NULL DEFAULT 0,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);
