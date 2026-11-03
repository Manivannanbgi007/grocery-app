-- The local contact book: user id -> name and email (from UserRegistered events).
CREATE TABLE contacts (
    user_id    BIGINT        NOT NULL COMMENT 'User id from auth-service',
    full_name  VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  NOT NULL,
    phone      VARCHAR(15)   NULL,
    role       VARCHAR(20)   NOT NULL,
    PRIMARY KEY (user_id)
);

-- Store id -> the vendor who owns it (from StoreRegistered / StoreApproved events).
CREATE TABLE store_contacts (
    store_id       BIGINT        NOT NULL COMMENT 'Store id from store-service',
    owner_user_id  BIGINT        NOT NULL,
    store_name     VARCHAR(100)  NOT NULL,
    PRIMARY KEY (store_id)
);

-- In-app notifications (the bell icon), with what happened to their email copy.
CREATE TABLE notifications (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    user_id       BIGINT         NOT NULL,
    type          VARCHAR(30)    NOT NULL,
    title         VARCHAR(150)   NOT NULL,
    message       VARCHAR(1000)  NOT NULL,
    reference_id  BIGINT         NULL     COMMENT 'Order, store or product id',
    is_read       BOOLEAN        NOT NULL DEFAULT FALSE,
    read_at       DATETIME(6)    NULL,
    email_to      VARCHAR(150)   NULL,
    email_status  VARCHAR(20)    NOT NULL COMMENT 'SENT, FAILED or NOT_SENT',
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_read (user_id, is_read),
    INDEX idx_notifications_email_status (email_status)
);
