-- Delivery zones: PIN codes served by a group of partners, and what a partner earns per delivery there.
CREATE TABLE delivery_zones (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)   NOT NULL,
    city            VARCHAR(60)    NOT NULL,
    partner_payout  DECIMAL(10,2)  NOT NULL COMMENT 'Earned by the partner per delivery',
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_delivery_zones_name UNIQUE (name)
);

CREATE TABLE delivery_zone_pincodes (
    zone_id  BIGINT      NOT NULL,
    pincode  VARCHAR(6)  NOT NULL,
    PRIMARY KEY (zone_id, pincode),
    CONSTRAINT fk_delivery_zone_pincodes_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones (id)
);

-- Delivery partners (one per DELIVERY_PARTNER user). "Online" is kept in Redis, not here.
CREATE TABLE delivery_partners (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT        NOT NULL COMMENT 'User id from auth-service',
    full_name               VARCHAR(100)  NOT NULL,
    phone                   VARCHAR(15)   NOT NULL,
    vehicle_type            VARCHAR(20)   NULL,
    vehicle_number          VARCHAR(20)   NULL,
    driving_license_number  VARCHAR(30)   NULL,
    zone_id                 BIGINT        NULL,
    kyc_status              VARCHAR(20)   NOT NULL COMMENT 'NOT_SUBMITTED, PENDING, APPROVED, REJECTED',
    kyc_note                VARCHAR(255)  NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_delivery_partners_user_id UNIQUE (user_id),
    CONSTRAINT fk_delivery_partners_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones (id),
    INDEX idx_delivery_partners_zone_kyc (zone_id, kyc_status)
);

-- One delivery per order, created from OrderReadyForPickup. Both addresses are copies taken from the event.
CREATE TABLE deliveries (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    order_id           BIGINT         NOT NULL,
    customer_user_id   BIGINT         NOT NULL,
    store_id           BIGINT         NOT NULL,
    zone_id            BIGINT         NULL COMMENT 'Zone of the store; NULL = no zone covers it',
    store_name         VARCHAR(100)   NOT NULL,
    store_phone        VARCHAR(15)    NOT NULL,
    store_address      VARCHAR(400)   NOT NULL,
    store_pincode      VARCHAR(6)     NOT NULL,
    customer_name      VARCHAR(100)   NOT NULL,
    customer_phone     VARCHAR(15)    NOT NULL,
    customer_address   VARCHAR(400)   NOT NULL,
    customer_pincode   VARCHAR(6)     NOT NULL,
    payment_method     VARCHAR(10)    NOT NULL,
    amount_to_collect  DECIMAL(10,2)  NOT NULL,
    partner_user_id    BIGINT         NULL,
    status             VARCHAR(30)    NOT NULL,
    issue              VARCHAR(30)    NULL,
    issue_note         VARCHAR(255)   NULL,
    proof_photo_url    VARCHAR(500)   NULL,
    partner_payout     DECIMAL(10,2)  NULL,
    assigned_at        DATETIME(6)    NULL,
    picked_up_at       DATETIME(6)    NULL,
    delivered_at       DATETIME(6)    NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_deliveries_order_id UNIQUE (order_id),
    INDEX idx_deliveries_partner_status (partner_user_id, status),
    INDEX idx_deliveries_zone_status (zone_id, status)
);

-- Partners who rejected a delivery (it is not offered to them again).
CREATE TABLE delivery_rejections (
    delivery_id      BIGINT  NOT NULL,
    partner_user_id  BIGINT  NOT NULL,
    PRIMARY KEY (delivery_id, partner_user_id),
    CONSTRAINT fk_delivery_rejections_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id)
);
