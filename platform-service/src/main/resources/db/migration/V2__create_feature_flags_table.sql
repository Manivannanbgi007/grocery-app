-- Switches for features that are built but not open to everybody yet (e.g. a new city, delivery slots).
CREATE TABLE feature_flags (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    flag_key            VARCHAR(60)   NOT NULL COMMENT 'UPPER_SNAKE_CASE, e.g. DELIVERY_SLOTS',
    description         VARCHAR(255)  NOT NULL,
    enabled             BOOLEAN       NOT NULL,
    updated_by_user_id  BIGINT        NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_feature_flags_key UNIQUE (flag_key)
);

INSERT INTO feature_flags (flag_key, description, enabled, created_at, updated_at) VALUES
 ('DELIVERY_SLOTS',    'Customers choose a delivery time slot at checkout',  FALSE, NOW(6), NOW(6)),
 ('CITY_HYDERABAD',    'Stores and customers in Hyderabad can use the app',  FALSE, NOW(6), NOW(6)),
 ('PRODUCT_REVIEWS',   'Customers can review the products they received',    TRUE,  NOW(6), NOW(6)),
 ('RAZORPAY_PAYMENTS', 'Online payments go through Razorpay instead of mock', FALSE, NOW(6), NOW(6));
