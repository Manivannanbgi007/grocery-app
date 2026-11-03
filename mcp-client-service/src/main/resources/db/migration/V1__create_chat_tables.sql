-- Who a conversation belongs to. The id is a random UUID made by mcp-client-service.
CREATE TABLE conversations (
    id               VARCHAR(36)  NOT NULL,
    owner_user_id    BIGINT       NULL     COMMENT 'User id from auth-service; NULL for a guest',
    created_at       DATETIME(6)  NOT NULL,
    last_message_at  DATETIME(6)  NOT NULL COMMENT 'The nightly clean-up deletes old conversations',
    PRIMARY KEY (id),
    INDEX idx_conversations_last_message_at (last_message_at)
);

-- The messages of every conversation, in the layout of Spring AI's JdbcChatMemoryRepository (MySQL dialect).
-- Spring AI ships its own schema-mysql.sql, but it uses CREATE INDEX IF NOT EXISTS, which MySQL 8 does not support,
-- and in this project Flyway creates every table. The name must stay in capitals: the repository's SQL uses it so.
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id  VARCHAR(36)  NOT NULL,
    content          TEXT         NOT NULL,
    type             VARCHAR(10)  NOT NULL COMMENT 'USER or ASSISTANT',
    `timestamp`      TIMESTAMP    NOT NULL,
    sequence_id      BIGINT       NOT NULL COMMENT 'Order of the messages in the conversation',
    CONSTRAINT chk_chat_memory_type CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    INDEX idx_chat_memory_conversation_sequence (conversation_id, sequence_id)
);
