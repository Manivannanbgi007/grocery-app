-- Password-reset tokens are stored as their SHA-256 hash, like refresh tokens (docs/authentication.md): a copy of the
-- database contains no usable token. The tokens issued before this change are hashed in place (MySQL's SHA2 gives the
-- same lowercase hex as Sha256.hex in Java), so they keep working until they expire.
UPDATE password_reset_tokens SET token = SHA2(token, 256);

ALTER TABLE password_reset_tokens
    CHANGE COLUMN token token_hash CHAR(64) NOT NULL,
    RENAME INDEX uk_password_reset_tokens_token TO uk_password_reset_tokens_token_hash,
    ADD INDEX idx_password_reset_tokens_expires_at (expires_at);   -- HousekeepingJob deletes expired tokens
