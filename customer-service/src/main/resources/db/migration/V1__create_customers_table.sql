-- Customer profiles. Created from the UserRegistered event (auth-service owns login data).
CREATE TABLE customers (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT 'User id from auth-service',
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    phone          VARCHAR(15)  NOT NULL COMMENT 'Contact number for deliveries',
    date_of_birth  DATE         NULL,
    gender         VARCHAR(20)  NULL     COMMENT 'MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY',
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_user_id UNIQUE (user_id)
);
