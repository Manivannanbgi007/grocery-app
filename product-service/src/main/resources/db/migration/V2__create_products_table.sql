-- Products of all stores (every row belongs to exactly one store: the multi-tenant rule).
-- This is the source of truth. Debezium copies every change to Kafka (cdc.product_db.products)
-- and product-read-service indexes it in Elasticsearch for searching.
CREATE TABLE products (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    store_id             BIGINT         NOT NULL COMMENT 'Store id from store-service',
    category_id          BIGINT         NOT NULL,
    name                 VARCHAR(150)   NOT NULL,
    description          VARCHAR(1000)  NULL,
    brand                VARCHAR(60)    NULL,
    sku                  VARCHAR(40)    NOT NULL COMMENT 'Vendor product code, unique per store',
    unit                 VARCHAR(10)    NOT NULL COMMENT 'KG, G, L, ML, PIECE, PACK, DOZEN',
    unit_quantity        DECIMAL(10,3)  NOT NULL,
    price                DECIMAL(10,2)  NOT NULL COMMENT 'Before discount (MRP), INR',
    discount_percentage  DECIMAL(5,2)   NOT NULL DEFAULT 0,
    selling_price        DECIMAL(10,2)  NOT NULL COMMENT 'price minus discount, calculated by the Product entity',
    tax_percentage       DECIMAL(5,2)   NOT NULL COMMENT 'GST',
    image_urls           JSON           NOT NULL COMMENT 'List of image URLs',
    status               VARCHAR(10)    NOT NULL COMMENT 'ACTIVE, INACTIVE, BLOCKED, DELETED',
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0 COMMENT 'Also used by Elasticsearch to ignore stale changes',
    PRIMARY KEY (id),
    INDEX idx_products_store_id_sku (store_id, sku),
    INDEX idx_products_category_id (category_id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
