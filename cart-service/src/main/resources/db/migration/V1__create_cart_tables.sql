-- One cart per customer. All items of a cart come from ONE store (store_id), because one order goes to one store.
CREATE TABLE carts (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    customer_user_id  BIGINT       NOT NULL COMMENT 'Customer user id from auth-service',
    store_id          BIGINT       NULL     COMMENT 'Store of the items; NULL while the cart is empty',
    coupon_code       VARCHAR(20)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_carts_customer_user_id UNIQUE (customer_user_id)
);

-- Products in a cart, with a copy of their name and prices (prices include tax).
CREATE TABLE cart_items (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    cart_id         BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL COMMENT 'Product id from product-service',
    product_name    VARCHAR(150)   NOT NULL,
    unit            VARCHAR(20)    NOT NULL,
    unit_quantity   DECIMAL(10,3)  NOT NULL,
    image_url       VARCHAR(500)   NULL,
    price           DECIMAL(10,2)  NOT NULL COMMENT 'One unit before the product discount (MRP)',
    selling_price   DECIMAL(10,2)  NOT NULL COMMENT 'One unit, what the customer pays',
    tax_percentage  DECIMAL(5,2)   NOT NULL,
    quantity        INT            NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity BETWEEN 1 AND 20)
);
