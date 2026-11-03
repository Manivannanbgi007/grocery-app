package com.grocery.mcpclient.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The messages of a conversation, oldest first (for example to show the chat again after a page reload).
 * Only the last app.chat.history-messages messages are kept.
 */
@Schema(description = "A conversation with the shopping assistant")
public record ConversationResponse(UUID conversationId, List<Line> messages) {

    /**
     * @param role     USER (the customer) or ASSISTANT
     * @param text     the message text
     * @param action   the card shown under this answer, as it was then; null without one
     * @param approval the card's human approval (PENDING, APPROVED or REJECTED, and when); null without a card
     */
    @Schema(description = "One message of the conversation")
    public record Line(
            @Schema(example = "USER") String role,
            String text,
            @Schema(description = "The card shown under this answer; null without one") ChatAction action,
            @Schema(description = "The card's human approval; null without a card") ChatApprovalResponse approval) {

        /** A message without a card. */
        public Line(String role, String text) {
            this(role, text, null, null);
        }
    }
}
