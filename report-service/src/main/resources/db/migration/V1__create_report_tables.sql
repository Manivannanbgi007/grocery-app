-- report-service keeps READ MODELS: copies of what other services own, shaped for reports.
-- Everything here is built from events and can be rebuilt by replaying the topics (new consumer group).

-- One row per order, updated as the order moves on.
CREATE TABLE order_facts (
    order_id               BIGINT         NOT NULL,
    customer_user_id       BIGINT         NOT NULL,
    store_id               BIGINT         NOT NULL,
    status                 VARCHAR(20)    NOT NULL COMMENT 'PENDING, CONFIRMED, DELIVERED, CANCELLED, REJECTED, DELIVERY_FAILED',
    sale                   BOOLEAN        NOT NULL COMMENT 'true while the order counts as a sale (confirmed and not ended without delivery)',
    cancelled              BOOLEAN        NOT NULL COMMENT 'true when it ended without a delivery',
    payment_method         VARCHAR(10)    NOT NULL,
    payment_status         VARCHAR(20)    NOT NULL COMMENT 'PENDING, PAID, CASH_ON_DELIVERY, REFUNDED',
    items_total            DECIMAL(10,2)  NOT NULL,
    coupon_discount        DECIMAL(10,2)  NOT NULL,
    delivery_fee           DECIMAL(10,2)  NOT NULL,
    total_amount           DECIMAL(10,2)  NOT NULL,
    commission_percentage  DECIMAL(5,2)   NULL,
    placed_at              DATETIME(6)    NOT NULL,
    placed_date            DATE           NOT NULL COMMENT 'India date, for daily reports',
    delivered_at           DATETIME(6)    NULL,
    delivered_date         DATE           NULL,
    ended_at               DATETIME(6)    NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id),
    INDEX idx_order_facts_placed_date (placed_date, store_id),
    INDEX idx_order_facts_delivered_date (delivered_date)
);

CREATE TABLE order_item_facts (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      BIGINT         NOT NULL,
    store_id      BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    product_name  VARCHAR(150)   NOT NULL,
    quantity      INT            NOT NULL,
    line_total    DECIMAL(10,2)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_item_facts_order (order_id),
    INDEX idx_order_item_facts_product (product_id)
);

CREATE TABLE store_facts (
    store_id       BIGINT        NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    owner_user_id  BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL COMMENT 'PENDING, ACTIVE, REJECTED, DISABLED',
    PRIMARY KEY (store_id)
);

CREATE TABLE user_facts (
    user_id        BIGINT        NOT NULL,
    role           VARCHAR(20)   NOT NULL,
    registered_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (user_id)
);

-- Latest stock per product (StockChanged carries the full stock, stock_version orders the changes).
CREATE TABLE stock_facts (
    product_id          BIGINT  NOT NULL,
    store_id            BIGINT  NOT NULL,
    available_quantity  INT     NOT NULL,
    stock_version       BIGINT  NOT NULL,
    PRIMARY KEY (product_id)
);

CREATE TABLE partner_facts (
    user_id     BIGINT        NOT NULL,
    full_name   VARCHAR(100)  NOT NULL,
    kyc_status  VARCHAR(20)   NOT NULL,
    PRIMARY KEY (user_id)
);

-- Ids of the Kafka events this service has already handled (idempotent consumer).
CREATE TABLE processed_event (
    event_id      VARCHAR(36)  NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
);
