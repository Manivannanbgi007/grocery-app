package com.grocery.mcpclient.service.impl;

import com.grocery.mcpclient.dto.response.ChatAction;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.entity.ChatApproval;
import com.grocery.mcpclient.entity.Conversation;
import com.grocery.mcpclient.exception.ResourceNotFoundException;
import com.grocery.mcpclient.repository.ChatApprovalRepository;
import com.grocery.mcpclient.repository.ConversationRepository;
import com.grocery.mcpclient.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatApprovalRepository approvalRepository;
    private final ConversationRepository conversationRepository;
    private final Clock clock;

    @Override
    @Transactional
    public ChatApprovalResponse open(Conversation conversation, String answer, ChatAction action) {
        ChatApproval approval = approvalRepository.save(
                ChatApproval.pending(conversation.getId(), answer, JSON.writeValueAsString(action), clock.instant()));
        log.info("Conversation {}: card {} ({}) waits for the customer's approval", conversation.getId(), approval.getId(),
                action.kind());
        return ChatApprovalResponse.of(approval);
    }

    @Override
    @Transactional
    public ChatApprovalResponse decide(UUID approvalId, ApprovalStatus decision, Long userId) {
        // Someone else's card is answered like a missing one; a guest has no cards at all
        ChatApproval approval = approvalRepository.findById(approvalId.toString())
                .filter(found -> userId != null && conversationRepository.findById(found.getConversationId())
                        .map(conversation -> conversation.belongsTo(userId))
                        .orElse(false))
                .orElseThrow(() -> new ResourceNotFoundException("This card was not found."));
        approval.decide(decision, userId, clock.instant());
        log.info("Conversation {}: card {} is {}", approval.getConversationId(), approval.getId(), approval.getStatus());
        return ChatApprovalResponse.of(approval);
    }
}
