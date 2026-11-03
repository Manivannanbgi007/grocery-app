package com.grocery.mcpclient.service;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.entity.Conversation;

import java.util.UUID;

/**
 * The human approval of the chat's cards: every card starts PENDING, and the customer's click approves or rejects it.
 * Only the status is kept here; the order or the cart is changed by the customer's own browser, as before.
 */
public interface ApprovalService {

    /** A card is shown under this answer: it waits for the customer's decision (PENDING) */
    ChatApprovalResponse open(Conversation conversation, String answer, ChatAction action);

    /**
     * The customer approved or rejected a card of their own conversation.
     *
     * @throws com.grocery.mcpclient.exception.ResourceNotFoundException when the card does not exist or is not the
     *                                                                   user's (the same answer, so nobody learns ids)
     * @throws com.grocery.mcpclient.exception.BusinessException         when it was already decided the other way
     */
    ChatApprovalResponse decide(UUID approvalId, ApprovalStatus decision, Long userId);
}
