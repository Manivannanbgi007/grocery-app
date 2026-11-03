package com.grocery.mcpclient.dto.response;

import com.grocery.mcpclient.entity.ChatApproval;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The human approval of a card of the chat: does it still wait for the customer's click, or did they approve or
 * reject it, and when.
 *
 * @param id              the card's id: the browser sends the decision to POST /api/v1/chat/approvals/{id}
 * @param status          PENDING, APPROVED or REJECTED
 * @param createdAt       when the card was shown (it can be confirmed for 10 minutes)
 * @param decidedAt       when the customer approved or rejected it; null while PENDING
 * @param decidedByUserId the user who decided (the conversation's owner); null while PENDING
 */
@Schema(description = "The human approval of a card of the chat")
public record ChatApprovalResponse(
        @Schema(description = "The card's id", example = "5d1f0b4e-9f2a-4a51-8f43-0c2b7a1e9d10") String id,
        @Schema(description = "PENDING, APPROVED or REJECTED", example = "APPROVED") String status,
        @Schema(description = "When the card was shown (UTC)") Instant createdAt,
        @Schema(description = "When the customer approved or rejected it (UTC); null while PENDING") Instant decidedAt,
        @Schema(description = "Who approved or rejected it (the conversation's owner); null while PENDING", example = "7")
        Long decidedByUserId) {

    public static ChatApprovalResponse of(ChatApproval approval) {
        return new ChatApprovalResponse(approval.getId(), approval.getStatus().name(), approval.getCreatedAt(),
                approval.getDecidedAt(), approval.getDecidedBy());
    }
}
