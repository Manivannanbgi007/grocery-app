-- One row per login session (one login on one browser or device), docs/authentication.md.
-- The refresh token itself is never stored: only its SHA-256 hash. On every refresh the token is replaced
-- (rotation): the old hash moves to previous_token_hash, so a stolen OLD token can be recognised when it comes back.
CREATE TABLE user_sessions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    token_hash           CHAR(64)     NOT NULL COMMENT 'SHA-256 (hex) of the current refresh token',
    previous_token_hash  CHAR(64)     NULL     COMMENT 'SHA-256 of the token before the last rotation',
    rotated_at           DATETIME(6)  NULL     COMMENT 'When the token was last replaced',
    expires_at           DATETIME(6)  NOT NULL COMMENT 'The current token expires: last refresh + refresh-token-lifetime',
    session_expires_at   DATETIME(6)  NOT NULL COMMENT 'The session ends at the latest: login + max-lifetime',
    revoked_at           DATETIME(6)  NULL     COMMENT 'Set when the session was ended (logout, password change ...)',
    revoked_reason       VARCHAR(30)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(50)  NULL,
    updated_by           VARCHAR(50)  NULL,
    version              BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_sessions_token_hash UNIQUE (token_hash),
    INDEX idx_user_sessions_previous_token_hash (previous_token_hash),
    INDEX idx_user_sessions_user (user_id),
    INDEX idx_user_sessions_expires_at (expires_at),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);
