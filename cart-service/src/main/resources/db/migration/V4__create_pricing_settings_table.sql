-- Phase 11: delivery fee, free delivery amount and minimum order come from platform-service (PlatformSettingsChanged).
-- ONE row (id 1). It starts with the platform's default values and changes when the super admin changes them.
CREATE TABLE pricing_settings (
    id                   BIGINT         NOT NULL,
    delivery_fee         DECIMAL(10,2)  NOT NULL,
    free_delivery_from   DECIMAL(10,2)  NOT NULL,
    minimum_order_value  DECIMAL(10,2)  NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

INSERT INTO pricing_settings (id, delivery_fee, free_delivery_from, minimum_order_value, created_at, updated_at)
VALUES (1, 30.00, 299.00, 99.00, NOW(6), NOW(6));
