package com.grocery.mcpclient.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The customer's decision on a card of the chat, sent by the website right after the click: APPROVED (Confirm, after
 * the order was placed or cancelled or the cart changed) or REJECTED (Cancel, Keep it).
 */
@Schema(description = "The customer's decision on a card of the chat")
public record ApprovalDecisionRequest(
        @Schema(description = "APPROVED or REJECTED", example = "APPROVED")
        @NotNull(message = "decision is required")
        @Pattern(regexp = "APPROVED|REJECTED", message = "decision must be APPROVED or REJECTED")
        String decision) {
}
