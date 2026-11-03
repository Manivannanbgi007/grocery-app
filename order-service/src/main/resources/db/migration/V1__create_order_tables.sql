-- Orders. One order = one store (the cart's store). Store details, the delivery address and all
-- amounts are COPIES taken when the order was placed: later changes elsewhere do not change the order.
CREATE TABLE orders (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    customer_user_id         BIGINT         NOT NULL COMMENT 'Customer user id from auth-service',
    store_id                 BIGINT         NOT NULL,
    store_name               VARCHAR(100)   NOT NULL,
    store_phone              VARCHAR(15)    NOT NULL,
    store_address            VARCHAR(400)   NOT NULL,
    store_pincode            VARCHAR(6)     NOT NULL,
    delivery_contact_name    VARCHAR(100)   NOT NULL,
    delivery_contact_phone   VARCHAR(15)    NOT NULL,
    delivery_address_line1   VARCHAR(150)   NOT NULL,
    delivery_address_line2   VARCHAR(150)   NULL,
    delivery_landmark        VARCHAR(100)   NULL,
    delivery_city            VARCHAR(60)    NOT NULL,
    delivery_state           VARCHAR(60)    NOT NULL,
    delivery_pincode         VARCHAR(6)     NOT NULL,
    delivery_latitude        DECIMAL(9,6)   NULL,
    delivery_longitude       DECIMAL(9,6)   NULL,
    status                   VARCHAR(30)    NOT NULL COMMENT 'PENDING, CONFIRMED, ACCEPTED, PACKED, READY_FOR_PICKUP, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REJECTED',
    payment_method           VARCHAR(10)    NOT NULL COMMENT 'COD or ONLINE',
    payment_status           VARCHAR(20)    NOT NULL COMMENT 'PENDING, PAID, CASH_ON_DELIVERY, FAILED, REFUNDED',
    items_total              DECIMAL(10,2)  NOT NULL,
    coupon_code              VARCHAR(20)    NULL,
    coupon_discount          DECIMAL(10,2)  NOT NULL,
    delivery_fee             DECIMAL(10,2)  NOT NULL,
    tax_included             DECIMAL(10,2)  NOT NULL,
    total_amount             DECIMAL(10,2)  NOT NULL COMMENT 'items_total - coupon_discount + delivery_fee',
    customer_note            VARCHAR(250)   NULL,
    status_reason            VARCHAR(255)   NULL     COMMENT 'Why it was cancelled or rejected',
    cancelled_by             VARCHAR(20)    NULL,
    delivered_at             DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_orders_customer (customer_user_id, id),
    INDEX idx_orders_store_status (store_id, status),
    INDEX idx_orders_status (status)
);

-- The products of an order, with their name and price at the time of the order.
CREATE TABLE order_items (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    order_id        BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    product_name    VARCHAR(150)   NOT NULL,
    unit            VARCHAR(20)    NOT NULL,
    unit_quantity   DECIMAL(10,3)  NOT NULL,
    image_url       VARCHAR(500)   NULL,
    price           DECIMAL(10,2)  NOT NULL COMMENT 'One unit before the product discount (MRP)',
    unit_price      DECIMAL(10,2)  NOT NULL COMMENT 'One unit, what the customer paid',
    tax_percentage  DECIMAL(5,2)   NOT NULL,
    quantity        INT            NOT NULL,
    line_total      DECIMAL(10,2)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- Every status change: the tracking timeline. Rows are only added.
CREATE TABLE order_status_history (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    status      VARCHAR(30)   NOT NULL,
    note        VARCHAR(255)  NULL,
    changed_by  VARCHAR(20)   NOT NULL COMMENT 'CUSTOMER, VENDOR, ADMIN, DELIVERY_PARTNER or SYSTEM',
    changed_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
