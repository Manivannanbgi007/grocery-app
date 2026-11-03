-- Discount codes. store_id NULL = platform coupon (created by an admin, valid in every store);
-- otherwise a store coupon created by that store's vendor.
CREATE TABLE coupons (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(20)    NOT NULL COMMENT 'Upper case, e.g. WELCOME50',
    description          VARCHAR(255)   NOT NULL,
    store_id             BIGINT         NULL,
    discount_type        VARCHAR(20)    NOT NULL COMMENT 'PERCENTAGE or FLAT',
    discount_value       DECIMAL(10,2)  NOT NULL COMMENT 'Percent or amount',
    max_discount_amount  DECIMAL(10,2)  NULL     COMMENT 'Cap for PERCENTAGE coupons; NULL = no cap',
    min_order_amount     DECIMAL(10,2)  NOT NULL DEFAULT 0,
    valid_from           DATETIME(6)    NOT NULL,
    valid_until          DATETIME(6)    NOT NULL,
    usage_limit          INT            NULL     COMMENT 'Total uses allowed; NULL = unlimited',
    used_count           INT            NOT NULL DEFAULT 0,
    per_customer_limit   INT            NOT NULL DEFAULT 1,
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by_user_id   BIGINT         NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_coupons_code UNIQUE (code),
    INDEX idx_coupons_store_id (store_id)
);

-- Every use of a coupon by an order (written when order-service publishes OrderCreated, Phase 7).
-- order_id is unique, so the same order never counts twice.
CREATE TABLE coupon_redemptions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    coupon_id         BIGINT         NOT NULL,
    customer_user_id  BIGINT         NOT NULL,
    order_id          BIGINT         NOT NULL,
    discount_amount   DECIMAL(10,2)  NOT NULL,
    redeemed_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT uk_coupon_redemptions_order_id UNIQUE (order_id),
    INDEX idx_coupon_redemptions_coupon_customer (coupon_id, customer_user_id)
);
