-- The platform-wide settings: ONE row (id 1). The super admin changes them; cart-service gets them as an event.
CREATE TABLE platform_settings (
    id                   BIGINT         NOT NULL,
    currency             VARCHAR(3)     NOT NULL COMMENT 'ISO code, e.g. INR',
    delivery_fee         DECIMAL(10,2)  NOT NULL COMMENT 'Charged below free_delivery_from',
    free_delivery_from   DECIMAL(10,2)  NOT NULL COMMENT 'Items total from which delivery is free',
    minimum_order_value  DECIMAL(10,2)  NOT NULL,
    support_email        VARCHAR(120)   NOT NULL,
    support_phone        VARCHAR(20)    NOT NULL,
    updated_by_user_id   BIGINT         NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- Needed in every environment (not only locally), so it is part of the migration, not of db/seed.
INSERT INTO platform_settings (id, currency, delivery_fee, free_delivery_from, minimum_order_value, support_email, support_phone,
                               created_at, updated_at)
VALUES (1, 'INR', 30.00, 299.00, 99.00, 'support@grocery.com', '1800-123-4567', NOW(6), NOW(6));
