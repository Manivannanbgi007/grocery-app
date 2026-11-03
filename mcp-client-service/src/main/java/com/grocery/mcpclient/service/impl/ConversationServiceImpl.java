package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.dto.response.ConversationResponse;
import com.grocery.mcpclient.entity.ChatApproval;
import com.grocery.mcpclient.entity.Conversation;
import com.grocery.mcpclient.exception.ResourceNotFoundException;
import com.grocery.mcpclient.repository.ChatApprovalRepository;
import com.grocery.mcpclient.repository.ConversationRepository;
import com.grocery.mcpclient.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int CLEANUP_BATCH_SIZE = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ConversationRepository conversationRepository;
    private final ChatMemory chatMemory;
    private final ChatApprovalRepository approvalRepository;
    private final Clock clock;

    @Override
    public Conversation startOrContinue(UUID conversationId, Long userId) {
        if (conversationId == null) {
            Conversation conversation = conversationRepository.save(Conversation.start(userId, clock.instant()));
            log.debug("Started conversation {} for {}", conversation.getId(), userId == null ? "a guest" : "user " + userId);
            return conversation;
        }
        return findOwnConversation(conversationId, userId);
    }

    @Override
    public void messageAdded(Conversation conversation) {
        conversation.messageAdded(clock.instant());
        conversationRepository.save(conversation);
    }

    /**
     * The messages, each card back under the answer it was shown with, with its human approval ("✓ Approved").
     *
     * How a card finds its answer: the cards are stored with the answer's text (chat_approvals.answer). Going from the
     * newest message back, each answer takes the newest card with its text that is still left. So the same answer twice
     * ("Here is the summary of your order ...") gets its own cards in the right order, and the cards of answers that
     * are no longer kept (only the newest messages are) are left out.
     */
    @Override
    public ConversationResponse getConversation(UUID conversationId, Long userId) {
        Conversation conversation = findOwnConversation(conversationId, userId);
        List<Message> messages = chatMemory.get(conversation.getId()).stream()
                .filter(message -> message.getMessageType() == MessageType.USER
                        || message.getMessageType() == MessageType.ASSISTANT)
                .toList();
        List<ChatApproval> cardsLeft = new ArrayList<>(approvalRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId()));

        ConversationResponse.Line[] lines = new ConversationResponse.Line[messages.size()];
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            ChatApproval card = message.getMessageType() == MessageType.ASSISTANT ? takeCard(message.getText(), cardsLeft) : null;
            lines[index] = card == null ? toLine(message) : toLine(message, card);
        }
        return new ConversationResponse(conversationId, Arrays.asList(lines));
    }

    @Override
    public void deleteConversation(UUID conversationId, Long userId) {
        Conversation conversation = findOwnConversation(conversationId, userId);
        delete(conversation.getId());
        log.info("Deleted conversation {} on request", conversation.getId());
    }

    @Override
    public int deleteInactiveSince(Instant before) {
        int deleted = 0;
        List<String> ids;
        do {
            ids = conversationRepository.findIdsInactiveSince(before, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            ids.forEach(this::delete);
            deleted += ids.size();
        } while (ids.size() == CLEANUP_BATCH_SIZE);
        return deleted;
    }

    /**
     * Someone else's conversation is answered with the same 404 as a missing one, so nobody can find out
     * which conversation ids exist.
     */
    private Conversation findOwnConversation(UUID conversationId, Long userId) {
        return conversationRepository.findById(conversationId.toString())
                .filter(conversation -> conversation.belongsTo(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found. Start a new conversation by sending a message without conversationId."));
    }

    /** The messages first, then the owner row: a crash in between leaves no messages without an owner. */
    private void delete(String conversationId) {
        chatMemory.clear(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    /** The newest card left that was shown under an answer with this text (and takes it out of the list); null if none */
    private static ChatApproval takeCard(String answer, List<ChatApproval> cardsLeft) {
        for (ChatApproval card : cardsLeft) {
            if (card.getAnswer().equals(answer)) {
                cardsLeft.remove(card);
                return card;
            }
        }
        return null;
    }

    private static ConversationResponse.Line toLine(Message message) {
        return new ConversationResponse.Line(message.getMessageType().name(), message.getText());
    }

    /** An answer with its card and the card's approval; a card that cannot be read any more is left out */
    private static ConversationResponse.Line toLine(Message message, ChatApproval card) {
        try {
            ChatAction action = JSON.readValue(card.getAction(), ChatAction.class);
            return new ConversationResponse.Line(message.getMessageType().name(), message.getText(), action, ChatApprovalResponse.of(card));
        } catch (JacksonException ex) {
            log.warn("Card {} cannot be read ({}); the history shows its answer without it", card.getId(), ex.getOriginalMessage());
            return toLine(message);
        }
    }
}
