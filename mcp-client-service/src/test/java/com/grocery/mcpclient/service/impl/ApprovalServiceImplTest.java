package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.entity.ChatApproval;
import com.grocery.mcpclient.entity.Conversation;
import com.grocery.mcpclient.exception.ResourceNotFoundException;
import com.grocery.mcpclient.repository.ChatApprovalRepository;
import com.grocery.mcpclient.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Who may decide a card: only the owner of its conversation; for anybody else it does not exist. */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:01:30Z");

    @Mock
    private ChatApprovalRepository approvalRepository;
    @Mock
    private ConversationRepository conversationRepository;

    private ApprovalServiceImpl approvalService;
    private Conversation rahulsConversation;
    private ChatApproval card;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalServiceImpl(approvalRepository, conversationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        rahulsConversation = Conversation.start(7L, NOW.minusSeconds(120));
        card = ChatApproval.pending(rahulsConversation.getId(), "Here is the summary of your order.", "{\"kind\":\"PLACE_ORDER\"}", NOW.minusSeconds(90));
        when(approvalRepository.findById(card.getId())).thenReturn(Optional.of(card));
    }

    @Test
    void theOwnerApproves_andTheDecisionComesBack() {
        when(conversationRepository.findById(rahulsConversation.getId())).thenReturn(Optional.of(rahulsConversation));

        ChatApprovalResponse approved = approvalService.decide(UUID.fromString(card.getId()), ApprovalStatus.APPROVED, 7L);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decidedAt()).isEqualTo(NOW);
        assertThat(approved.decidedByUserId()).isEqualTo(7L);
    }

    @Test
    void someoneElsesCardIsNotFound() {
        when(conversationRepository.findById(rahulsConversation.getId())).thenReturn(Optional.of(rahulsConversation));

        assertThatThrownBy(() -> approvalService.decide(UUID.fromString(card.getId()), ApprovalStatus.APPROVED, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(card.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void aGuestDecidesNothing() {
        assertThatThrownBy(() -> approvalService.decide(UUID.fromString(card.getId()), ApprovalStatus.REJECTED, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(card.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }
}
