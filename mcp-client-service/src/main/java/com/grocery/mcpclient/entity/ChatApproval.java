package com.grocery.mcpclient.entity;

import com.grocery.mcpclient.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The human approval of one card of the chat (a row in chat_approvals): the card as the customer saw it, under which
 * answer, and the customer's decision with its time.
 *
 * The decision itself is made in the customer's browser: their click on Confirm places or cancels the order (or changes
 * the cart) with their own login, and the services check it there. This row only remembers what was decided, so the
 * chat history can show "✓ Approved" again later.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "chat_approvals")
public class ChatApproval {

    /** Random, so nobody can guess another person's card; the browser also uses it as the card's key */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 36, updatable = false)
    private String conversationId;

    /** The answer the card was shown under (ConversationServiceImpl puts the card back under it in the history) */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String answer;

    /** The card (ChatAction) as JSON */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ApprovalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The user id of who decided (only the conversation's owner can) */
    @Column(name = "decided_by")
    private Long decidedBy;

    /** A card that was just shown: it waits for the customer's decision */
    public static ChatApproval pending(String conversationId, String answer, String actionJson, Instant now) {
        ChatApproval approval = new ChatApproval();
        approval.id = UUID.randomUUID().toString();
        approval.conversationId = conversationId;
        approval.answer = answer;
        approval.action = actionJson;
        approval.status = ApprovalStatus.PENDING;
        approval.createdAt = now;
        return approval;
    }

    /**
     * The customer approved (Confirm) or rejected (Cancel, Keep it) the card. A decision is final: the same decision
     * again (a retry of the browser) changes nothing, the other one is refused.
     */
    public void decide(ApprovalStatus decision, Long userId, Instant now) {
        if (decision == ApprovalStatus.PENDING) {
            throw new BusinessException("A decision is APPROVED or REJECTED.");
        }
        if (status == decision) {
            return;
        }
        if (status != ApprovalStatus.PENDING) {
            throw new BusinessException("This card was already " + status.name().toLowerCase(Locale.ROOT) + ".");
        }
        this.status = decision;
        this.decidedAt = now;
        this.decidedBy = userId;
    }
}
