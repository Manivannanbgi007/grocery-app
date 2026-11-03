package com.grocery.mcpclient.service;

import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.dto.request.ChatMessageRequest;
import com.grocery.mcpclient.dto.response.ChatReplyResponse;

/**
 * Answers the customer's messages.
 */
public interface ChatService {

    /**
     * Answers one message: the model may call the shop's tools, and its answer is checked against the tool results.
     *
     * @param user who is chatting (UserContext.GUEST for a guest); a logged-in customer also gets the tools for
     *             their own cart and orders
     * @throws com.grocery.mcpclient.exception.AssistantUnavailableException when the model or the tools are down (503)
     * @throws com.grocery.mcpclient.exception.AssistantBusyException        when too many questions are in progress (503)
     */
    ChatReplyResponse sendMessage(ChatMessageRequest request, UserContext user);
}
