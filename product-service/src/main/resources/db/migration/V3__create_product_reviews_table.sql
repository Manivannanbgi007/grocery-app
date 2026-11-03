-- Phase 11: customers review the products they received; the product keeps its average (copied to search by Debezium).
ALTER TABLE products
    ADD COLUMN rating_average DECIMAL(2,1) NULL COMMENT 'Average of the visible reviews, e.g. 4.3' AFTER status,
    ADD COLUMN rating_count   INT          NOT NULL DEFAULT 0 COMMENT 'Number of visible reviews' AFTER rating_average;

CREATE TABLE product_reviews (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    store_id          BIGINT        NOT NULL COMMENT 'Copied from the product, so vendors see their reviews quickly',
    order_id          BIGINT        NOT NULL COMMENT 'The delivered order the product came with (order-service)',
    customer_user_id  BIGINT        NOT NULL,
    reviewer_name     VARCHAR(60)   NOT NULL COMMENT 'Shown publicly, e.g. "Priya S."',
    rating            INT           NOT NULL COMMENT '1 to 5 stars',
    comment           VARCHAR(1000) NULL,
    status            VARCHAR(10)   NOT NULL COMMENT 'VISIBLE or HIDDEN (by an admin)',
    hidden_reason     VARCHAR(255)  NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_reviews_order_product UNIQUE (order_id, product_id),
    INDEX idx_product_reviews_product (product_id, status),
    INDEX idx_product_reviews_store (store_id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id)
);
