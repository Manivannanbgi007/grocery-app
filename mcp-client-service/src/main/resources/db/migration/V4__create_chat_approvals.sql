-- Human approval of the chat's cards. Every card the assistant shows (an order to place, an order to cancel, a change
-- of the cart) waits for the customer's decision: PENDING until they press Confirm (APPROVED) or Cancel / Keep it
-- (REJECTED). The row keeps the card and the decision, so the chat history shows them again after a reload.
-- Deleted together with its conversation.
CREATE TABLE chat_approvals (
    id               VARCHAR(36)  NOT NULL COMMENT 'Random UUID; also the card''s key in the browser',
    conversation_id  VARCHAR(36)  NOT NULL,
    answer           TEXT         NOT NULL COMMENT 'The answer the card was shown under: puts it back there in the history',
    action           TEXT         NOT NULL COMMENT 'The card (ChatAction) as JSON',
    status           VARCHAR(10)  NOT NULL COMMENT 'PENDING, APPROVED or REJECTED',
    created_at       DATETIME(6)  NOT NULL,
    decided_at       DATETIME(6)  NULL     COMMENT 'When the customer approved or rejected it',
    decided_by       BIGINT       NULL     COMMENT 'User id of who approved or rejected it (the conversation''s owner)',
    PRIMARY KEY (id),
    INDEX idx_chat_approvals_conversation (conversation_id, created_at),
    CONSTRAINT chk_chat_approvals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_chat_approvals_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);
