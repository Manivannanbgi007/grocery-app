-- Social login with Google, Microsoft, Facebook and Apple (docs/social-login.md).

-- When this email address was proven to belong to the account holder: by a completed password reset (the code went to
-- that address) or by a provider that verified it. A social login is linked to an existing account automatically only
-- when its email is verified on both sides; otherwise the account's password is asked once.
ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL AFTER email;

-- The provider accounts linked to a user: one per provider and user, and a provider account belongs to one user only.
CREATE TABLE social_logins (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    provider          VARCHAR(20)  NOT NULL,   -- google, microsoft, facebook, apple
    provider_user_id  VARCHAR(255) NOT NULL,   -- the provider's stable id of the person ("sub"), never the email
    email             VARCHAR(150) NULL,       -- the email the provider gave when it was linked (for support only)
    last_used_at      DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(50)  NULL,
    updated_by        VARCHAR(50)  NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_logins_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_logins_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_social_logins_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- One row per social login attempt, from the click on the button until the user is logged in (or it expires):
--   STARTED        sent to the provider; the authorization request (state, nonce, PKCE verifier) is kept as JSON
--   CALLBACK       the provider came back with this state (used once)
--   AUTHENTICATED  the provider's answer was valid; a one-time code (60 s) was handed to the browser
--   REDEEMED       the code was exchanged; waiting for the sign-up form or the account's password
--   DONE           logged in
-- browser_hash ties the attempt to the browser that started it (cookie social_flow): another browser cannot finish it.
CREATE TABLE social_login_flows (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    state_hash             CHAR(64)     NOT NULL,
    browser_hash           CHAR(64)     NOT NULL,
    provider               VARCHAR(20)  NOT NULL,
    stage                  VARCHAR(20)  NOT NULL,
    authorization_request  TEXT         NULL,
    code_hash              CHAR(64)     NULL,
    provider_user_id       VARCHAR(255) NULL,
    email                  VARCHAR(150) NULL,
    email_verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    full_name              VARCHAR(100) NULL,
    expires_at             DATETIME(6)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_login_flows_state UNIQUE (state_hash),
    CONSTRAINT uk_social_login_flows_code UNIQUE (code_hash),
    INDEX idx_social_login_flows_browser (browser_hash),
    INDEX idx_social_login_flows_expires_at (expires_at)   -- HousekeepingJob deletes old attempts
);
