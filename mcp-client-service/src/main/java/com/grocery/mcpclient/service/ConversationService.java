package com.grocery.mcpclient.service;

import com.grocery.mcpclient.dto.response.ConversationResponse;
import com.grocery.mcpclient.entity.Conversation;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversations and who they belong to.
 */
public interface ConversationService {

    /**
     * Starts a new conversation (conversationId null) or continues an existing one of the same user.
     *
     * @throws com.grocery.mcpclient.exception.ResourceNotFoundException when the conversation does not exist or
     *                                                                   belongs to someone else
     */
    Conversation startOrContinue(UUID conversationId, Long userId);

    /** Remembers when the conversation had its last message (for the clean-up of old conversations). */
    void messageAdded(Conversation conversation);

    ConversationResponse getConversation(UUID conversationId, Long userId);

    void deleteConversation(UUID conversationId, Long userId);

    /** Deletes conversations without a message since the given time; returns how many were deleted. */
    int deleteInactiveSince(Instant before);
}
