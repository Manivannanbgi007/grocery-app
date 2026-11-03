package com.grocery.mcpclient.entity;

import com.grocery.mcpclient.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules of a card's human approval: PENDING until the customer decides, and a decision is final. */
class ChatApprovalTest {

    private static final Instant SHOWN = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant CLICKED = Instant.parse("2026-11-01T10:01:30Z");

    @Test
    void aNewCardWaitsForTheCustomer() {
        ChatApproval approval = ChatApproval.pending("conversation-1", "Here is the summary of your order.", "{\"kind\":\"PLACE_ORDER\"}", SHOWN);

        assertThat(approval.getId()).hasSize(36);
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getCreatedAt()).isEqualTo(SHOWN);
        assertThat(approval.getDecidedAt()).isNull();
        assertThat(approval.getDecidedBy()).isNull();
    }

    @Test
    void approvingKeepsWhenAndWho() {
        ChatApproval approval = pending();

        approval.decide(ApprovalStatus.APPROVED, 7L, CLICKED);

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getDecidedAt()).isEqualTo(CLICKED);
        assertThat(approval.getDecidedBy()).isEqualTo(7L);
    }

    @Test
    void theSameDecisionAgain_changesNothing() {
        ChatApproval approval = pending();
        approval.decide(ApprovalStatus.REJECTED, 7L, CLICKED);

        approval.decide(ApprovalStatus.REJECTED, 7L, CLICKED.plusSeconds(5));     // the browser's retry

        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getDecidedAt()).isEqualTo(CLICKED);
    }

    @Test
    void aDecisionIsFinal() {
        ChatApproval approval = pending();
        approval.decide(ApprovalStatus.APPROVED, 7L, CLICKED);

        assertThatThrownBy(() -> approval.decide(ApprovalStatus.REJECTED, 7L, CLICKED.plusSeconds(5)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This card was already approved.");
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void pendingIsNoDecision() {
        assertThatThrownBy(() -> pending().decide(ApprovalStatus.PENDING, 7L, CLICKED)).isInstanceOf(BusinessException.class);
    }

    private static ChatApproval pending() {
        return ChatApproval.pending("conversation-1", "Here is the summary of your order.", "{\"kind\":\"PLACE_ORDER\"}", SHOWN);
    }
}
