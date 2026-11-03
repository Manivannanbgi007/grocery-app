package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.TestcontainersConfiguration;
import com.grocery.mcpclient.dto.response.ConversationResponse;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.entity.ChatApproval;
import com.grocery.mcpclient.entity.CheckoutStep;
import com.grocery.mcpclient.entity.Conversation;
import com.grocery.mcpclient.exception.ResourceNotFoundException;
import com.grocery.mcpclient.repository.ChatApprovalRepository;
import com.grocery.mcpclient.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL (Testcontainers) + the Flyway schema + Spring AI's JdbcChatMemoryRepository:
 * proves the tables work for both, and that conversations are kept apart per user.
 * Not in a test transaction: the memory repository commits its own transactions, as in production.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationStorageTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:30:00Z");
    /** The fixed answer under every order summary, and a card as chat_approvals stores it (JSON) */
    private static final String SUMMARY = "Here is the summary of your order. Please check it and press Confirm to place the order.";
    private static final String PLACE_ORDER_CARD = "{\"kind\":\"PLACE_ORDER\",\"payable\":\"₹138\"}";

    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ChatApprovalRepository approvalRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ChatMemory chatMemory;
    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
        approvalRepository.deleteAll();
        conversationRepository.deleteAll();
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(JdbcChatMemoryRepository.builder()
                        .jdbcTemplate(jdbcTemplate)
                        .dialect(new MysqlChatMemoryRepositoryDialect())
                        .transactionManager(transactionManager)
                        .build())
                .maxMessages(4)
                .build();
        conversationService = new ConversationServiceImpl(conversationRepository, chatMemory, approvalRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void thePendingAdditionIsStoredWithItsConversation_andOnlyThere() {
        Conversation asked = conversationService.startOrContinue(null, 4L);
        Conversation other = conversationService.startOrContinue(null, 5L);
        asked.setPendingAdd(1L, "Tomato");
        conversationService.messageAdded(asked);

        Conversation reloaded = conversationService.startOrContinue(UUID.fromString(asked.getId()), 4L);
        assertThat(reloaded.getPendingProductId()).isEqualTo(1L);
        assertThat(reloaded.getPendingProductName()).isEqualTo("Tomato");
        assertThat(conversationService.startOrContinue(UUID.fromString(other.getId()), 5L).hasPendingAdd()).isFalse();

        reloaded.clearPendingAdd();
        conversationService.messageAdded(reloaded);
        assertThat(conversationService.startOrContinue(UUID.fromString(asked.getId()), 4L).hasPendingAdd()).isFalse();
    }

    @Test
    void theCheckoutStepIsStoredWithItsConversation_andOnlyThere() {
        Conversation paying = conversationService.startOrContinue(null, 4L);
        Conversation other = conversationService.startOrContinue(null, 5L);
        paying.setCheckoutStep(CheckoutStep.AWAITING_PAYMENT_METHOD);
        conversationService.messageAdded(paying);

        assertThat(conversationService.startOrContinue(UUID.fromString(paying.getId()), 4L).getCheckoutStep())
                .isEqualTo(CheckoutStep.AWAITING_PAYMENT_METHOD);
        assertThat(conversationService.startOrContinue(UUID.fromString(other.getId()), 5L).getCheckoutStep()).isNull();
    }

    @Test
    void messagesAreStoredAndOnlyTheNewestAreKept() {
        Conversation conversation = conversationService.startOrContinue(null, 4L);
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Hi"), new AssistantMessage("Hello!")));
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Price of Amul butter?"), new AssistantMessage("₹56.")));
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Is it in stock?"), new AssistantMessage("Only 3 left.")));

        ConversationResponse stored = conversationService.getConversation(UUID.fromString(conversation.getId()), 4L);

        assertThat(stored.messages()).extracting(ConversationResponse.Line::role)
                .containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
        assertThat(stored.messages()).extracting(ConversationResponse.Line::text)
                .containsExactly("Price of Amul butter?", "₹56.", "Is it in stock?", "Only 3 left.");
    }

    @Test
    void aConversationBelongsToItsUserOnly() {
        UUID usersConversation = UUID.fromString(conversationService.startOrContinue(null, 4L).getId());
        UUID guestsConversation = UUID.fromString(conversationService.startOrContinue(null, null).getId());

        assertThat(conversationService.startOrContinue(usersConversation, 4L).getOwnerUserId()).isEqualTo(4L);
        assertThat(conversationService.startOrContinue(guestsConversation, null).getOwnerUserId()).isNull();
        assertThatThrownBy(() -> conversationService.startOrContinue(usersConversation, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversationService.startOrContinue(usersConversation, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversationService.startOrContinue(guestsConversation, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversationService.startOrContinue(UUID.randomUUID(), 4L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingAConversationDeletesItsMessages() {
        Conversation conversation = conversationService.startOrContinue(null, 4L);
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Hi"), new AssistantMessage("Hello!")));

        conversationService.deleteConversation(UUID.fromString(conversation.getId()), 4L);

        assertThat(conversationRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY", Integer.class)).isZero();
    }

    /** The same fixed answer twice: each answer gets back its own card, with the decision on it */
    @Test
    void eachCardComesBackUnderItsOwnAnswer_withItsApproval() {
        Conversation conversation = conversationService.startOrContinue(null, 7L);
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Place my order, cash"), new AssistantMessage(SUMMARY)));
        ChatApproval first = approvalRepository.save(ChatApproval.pending(conversation.getId(), SUMMARY, PLACE_ORDER_CARD, NOW));
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Place my order again"), new AssistantMessage(SUMMARY)));
        ChatApproval second = approvalRepository.save(ChatApproval.pending(conversation.getId(), SUMMARY, PLACE_ORDER_CARD, NOW.plusSeconds(60)));
        first.decide(ApprovalStatus.REJECTED, 7L, NOW.plusSeconds(30));
        approvalRepository.save(first);
        second.decide(ApprovalStatus.APPROVED, 7L, NOW.plusSeconds(90));
        approvalRepository.save(second);

        ConversationResponse history = conversationService.getConversation(UUID.fromString(conversation.getId()), 7L);

        assertThat(history.messages()).extracting(line -> line.approval() == null ? null : line.approval().status())
                .containsExactly(null, "REJECTED", null, "APPROVED");
        ConversationResponse.Line approved = history.messages().get(3);
        assertThat(approved.action().kind()).isEqualTo("PLACE_ORDER");
        assertThat(approved.approval().id()).isEqualTo(second.getId());
        assertThat(approved.approval().decidedAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(approved.approval().decidedByUserId()).isEqualTo(7L);
    }

    /** Only the newest messages are kept: a card whose answer is gone is left out, the others still find theirs */
    @Test
    void theCardOfAnAnswerNoLongerKeptIsLeftOut() {
        Conversation conversation = conversationService.startOrContinue(null, 7L);
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Place my order, cash"), new AssistantMessage(SUMMARY)));
        approvalRepository.save(ChatApproval.pending(conversation.getId(), SUMMARY, PLACE_ORDER_CARD, NOW));
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Place my order again"), new AssistantMessage(SUMMARY)));
        ChatApproval kept = approvalRepository.save(ChatApproval.pending(conversation.getId(), SUMMARY, PLACE_ORDER_CARD, NOW.plusSeconds(60)));
        // the memory keeps 4 messages here: the first question and its summary drop out
        chatMemory.add(conversation.getId(), List.of(new UserMessage("When do you deliver?"), new AssistantMessage("From 7 am to 10 pm.")));

        ConversationResponse history = conversationService.getConversation(UUID.fromString(conversation.getId()), 7L);

        assertThat(history.messages()).extracting(ConversationResponse.Line::text)
                .containsExactly("Place my order again", SUMMARY, "When do you deliver?", "From 7 am to 10 pm.");
        assertThat(history.messages().get(1).approval().id()).isEqualTo(kept.getId());
        assertThat(history.messages().get(1).approval().status()).isEqualTo("PENDING");
        assertThat(history.messages().get(3).approval()).isNull();
    }

    @Test
    void deletingAConversationDeletesItsCards() {
        Conversation conversation = conversationService.startOrContinue(null, 7L);
        chatMemory.add(conversation.getId(), List.of(new UserMessage("Place my order, cash"), new AssistantMessage(SUMMARY)));
        approvalRepository.save(ChatApproval.pending(conversation.getId(), SUMMARY, PLACE_ORDER_CARD, NOW));

        conversationService.deleteConversation(UUID.fromString(conversation.getId()), 7L);

        assertThat(approvalRepository.count()).isZero();
    }

    @Test
    void theCleanUpDeletesOnlyOldConversations() {
        Conversation old = conversationService.startOrContinue(null, 4L);
        chatMemory.add(old.getId(), List.of(new UserMessage("Hi"), new AssistantMessage("Hello!")));
        jdbcTemplate.update("UPDATE conversations SET last_message_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.minus(Duration.ofDays(31))), old.getId());
        Conversation recent = conversationService.startOrContinue(null, 4L);

        int deleted = conversationService.deleteInactiveSince(NOW.minus(Duration.ofDays(30)));

        assertThat(deleted).isEqualTo(1);
        assertThat(conversationRepository.findAll()).extracting(Conversation::getId).containsExactly(recent.getId());
        assertThat(chatMemory.get(old.getId())).isEmpty();
        assertThat(TestTransaction.isActive()).isFalse();
    }
}
