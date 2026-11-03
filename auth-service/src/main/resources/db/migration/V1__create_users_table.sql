-- Users who can log in to the platform (all five roles).
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    phone          VARCHAR(15)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt hash, never the plain password',
    role           VARCHAR(30)  NOT NULL COMMENT 'SUPER_ADMIN, ADMIN, VENDOR, DELIVERY_PARTNER, CUSTOMER',
    status         VARCHAR(20)  NOT NULL COMMENT 'ACTIVE, DISABLED',
    store_id       BIGINT       NULL     COMMENT 'Vendor''s store (from store-service), set after approval',
    last_login_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    INDEX idx_users_role_status (role, status)
);
