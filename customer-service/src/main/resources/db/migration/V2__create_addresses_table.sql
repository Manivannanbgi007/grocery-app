-- Delivery addresses of customers (at most 10 each, exactly one default - enforced by the Customer entity).
CREATE TABLE addresses (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT        NOT NULL,
    label          VARCHAR(10)   NOT NULL COMMENT 'HOME, WORK, OTHER',
    contact_name   VARCHAR(100)  NOT NULL,
    contact_phone  VARCHAR(15)   NOT NULL,
    address_line1  VARCHAR(150)  NOT NULL,
    address_line2  VARCHAR(150)  NULL,
    landmark       VARCHAR(100)  NULL,
    city           VARCHAR(60)   NOT NULL,
    state          VARCHAR(60)   NOT NULL,
    pincode        VARCHAR(6)    NOT NULL,
    latitude       DECIMAL(9,6)  NULL,
    longitude      DECIMAL(9,6)  NULL,
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_addresses_customer_id (customer_id),
    CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);
