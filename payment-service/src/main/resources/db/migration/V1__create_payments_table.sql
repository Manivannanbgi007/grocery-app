-- One payment per order, created when order-service publishes OrderCreated.
CREATE TABLE payments (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_id            BIGINT         NOT NULL COMMENT 'Order id from order-service',
    customer_user_id    BIGINT         NOT NULL,
    store_id            BIGINT         NOT NULL,
    amount              DECIMAL(10,2)  NOT NULL,
    method              VARCHAR(10)    NOT NULL COMMENT 'COD or ONLINE',
    status              VARCHAR(20)    NOT NULL COMMENT 'PENDING, AWAITING_PAYMENT, PAID, FAILED, CASH_ON_DELIVERY, COLLECTED, CANCELLED, REFUNDED',
    gateway_order_id    VARCHAR(100)   NULL     COMMENT 'The gateway order the customer pays against (ONLINE)',
    gateway_payment_id  VARCHAR(100)   NULL,
    refund_reference    VARCHAR(100)   NULL,
    failure_reason      VARCHAR(255)   NULL,
    paid_at             DATETIME(6)    NULL,
    refunded_at         DATETIME(6)    NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    INDEX idx_payments_customer (customer_user_id),
    INDEX idx_payments_status (status)
);
