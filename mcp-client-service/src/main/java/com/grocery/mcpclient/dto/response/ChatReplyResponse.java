package com.grocery.mcpclient.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The assistant's answer.
 *
 * @param conversationId send it with the next message to continue the conversation
 * @param answer         plain text (show it as text, never as HTML)
 * @param grounded       true when the answer is based on the shop's live data and passed the fact check;
 *                       false for greetings, "I can only help with shopping" and "I can't answer that reliably"
 * @param toolsUsed      the tools the assistant called for this answer, e.g. ["searchProducts"]
 * @param links          pages of the website that fit the answer (at most 4; empty when the answer is not grounded),
 *                       made from the tool results by AnswerLinks
 * @param action         a summary with a Confirm button under the answer (the order summary of prepareMyOrder), made
 *                       from the tool results by ChatActions; null without one
 * @param paymentUrl     where to pay an online order that is placed and not paid yet ("proceed to pay" after the order),
 *                       exactly as order-service sent it (the order page); the website shows "Pay Now" for it. Null
 *                       otherwise: the chat never makes up a payment address
 * @param approval       the human approval of `action` (PENDING until the customer clicks; its id is the card's key);
 *                       null without a card
 */
@Schema(description = "The shopping assistant's answer")
public record ChatReplyResponse(
        @Schema(description = "Send it with the next message to continue the conversation") UUID conversationId,
        @Schema(description = "The answer as plain text", example = "Yes, Amul Butter (100 g) costs ₹56. Only 3 are left at Fresh Mart.")
        String answer,
        @Schema(description = "true when the answer comes from the shop's live data and passed the fact check") boolean grounded,
        @Schema(description = "Tools called for this answer", example = "[\"searchProducts\"]") List<String> toolsUsed,
        @Schema(description = "Pages of the website that fit the answer") List<ChatLink> links,
        @Schema(description = "A summary under the answer that the customer can confirm with a button; null without one")
        ChatAction action,
        @Schema(description = "Where to pay an online order that waits for its payment (order-service's own address, for "
                + "the Pay Now button); null otherwise", example = "/orders/207")
        String paymentUrl,
        @Schema(description = "The human approval of the card (PENDING until the customer clicks); null without a card")
        ChatApprovalResponse approval) {

    /** An answer without a human approval. */
    public ChatReplyResponse(UUID conversationId, String answer, boolean grounded, List<String> toolsUsed, List<ChatLink> links,
                             ChatAction action, String paymentUrl) {
        this(conversationId, answer, grounded, toolsUsed, links, action, paymentUrl, null);
    }

    /** An answer without a payment address. */
    public ChatReplyResponse(UUID conversationId, String answer, boolean grounded, List<String> toolsUsed, List<ChatLink> links,
                             ChatAction action) {
        this(conversationId, answer, grounded, toolsUsed, links, action, null);
    }

    /** An answer without a summary to confirm. */
    public ChatReplyResponse(UUID conversationId, String answer, boolean grounded, List<String> toolsUsed, List<ChatLink> links) {
        this(conversationId, answer, grounded, toolsUsed, links, null);
    }

    /** An answer without links. */
    public ChatReplyResponse(UUID conversationId, String answer, boolean grounded, List<String> toolsUsed) {
        this(conversationId, answer, grounded, toolsUsed, List.of());
    }
}
