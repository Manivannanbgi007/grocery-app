-- Stock of each product. One row per product (a product belongs to exactly one store).
-- available units = quantity_on_hand - reserved_quantity (calculated, not stored).
CREATE TABLE stock_items (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    product_id           BIGINT       NOT NULL COMMENT 'Product id from product-service',
    store_id             BIGINT       NOT NULL COMMENT 'Store that sells the product (store-service)',
    quantity_on_hand     INT          NOT NULL COMMENT 'Units physically in the store',
    reserved_quantity    INT          NOT NULL DEFAULT 0 COMMENT 'Units held for orders that are not delivered yet',
    low_stock_threshold  INT          NOT NULL COMMENT 'Low stock when available units <= this number',
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_items_product_id UNIQUE (product_id),
    -- Safety net: the Stock entity already enforces these rules
    CONSTRAINT chk_stock_items_quantities CHECK (quantity_on_hand >= 0 AND reserved_quantity >= 0
                                                 AND reserved_quantity <= quantity_on_hand AND low_stock_threshold >= 0),
    INDEX idx_stock_items_store_id (store_id)
);
