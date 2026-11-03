-- Units held for an order (one reservation per order), so a cancellation gives back exactly those units.
CREATE TABLE stock_reservations (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL COMMENT 'Order id from order-service',
    store_id    BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL COMMENT 'RESERVED, RELEASED or COMMITTED',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_reservations_order_id UNIQUE (order_id)
);

CREATE TABLE stock_reservation_items (
    id              BIGINT  NOT NULL AUTO_INCREMENT,
    reservation_id  BIGINT  NOT NULL,
    product_id      BIGINT  NOT NULL,
    quantity        INT     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_reservation_items_reservation FOREIGN KEY (reservation_id) REFERENCES stock_reservations (id)
);
