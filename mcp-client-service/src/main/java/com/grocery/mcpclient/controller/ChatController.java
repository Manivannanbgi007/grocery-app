package com.grocery.mcpclient.controller;

import com.grocery.mcpclient.context.UserContextHolder;
import com.grocery.mcpclient.dto.common.ApiResponse;
import com.grocery.mcpclient.dto.request.ApprovalDecisionRequest;
import com.grocery.mcpclient.dto.request.ChatMessageRequest;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.dto.response.ChatReplyResponse;
import com.grocery.mcpclient.dto.response.ConversationResponse;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.service.ApprovalService;
import com.grocery.mcpclient.service.ChatService;
import com.grocery.mcpclient.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The chat API, for guests and logged-in users (the gateway route /api/v1/chat/** needs no login).
 * A logged-in user's conversations can only be read and continued by that user.
 */
@Tag(name = "Shopping assistant", description = "Ask questions about products, prices, offers, delivery and stores; "
        + "vendors, delivery partners and admins also about their own work")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ApprovalService approvalService;

    @Operation(summary = "Send a message",
            description = "Leave conversationId empty for a new conversation. Answers can take several seconds.")
    @PostMapping("/messages")
    public ApiResponse<ChatReplyResponse> sendMessage(@Valid @RequestBody ChatMessageRequest request) {
        ChatReplyResponse reply = chatService.sendMessage(request, UserContextHolder.get());
        return ApiResponse.success("Answer ready", reply);
    }

    @Operation(summary = "Get the messages of a conversation", description = "Oldest first; only the latest messages are kept.")
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationResponse> getConversation(@PathVariable UUID conversationId) {
        return ApiResponse.success("Conversation found",
                conversationService.getConversation(conversationId, UserContextHolder.currentUserId()));
    }

    @Operation(summary = "Record the customer's decision on a card (human approval)",
            description = "The website sends it right after the click: APPROVED after Confirm did the order or cart change, "
                    + "REJECTED after Cancel or Keep it. Only the decision is stored (the chat history shows it); the order "
                    + "or the cart itself is changed by the website's own calls, as before. A decision is final.")
    @PostMapping("/approvals/{approvalId}")
    public ApiResponse<ChatApprovalResponse> decideApproval(@PathVariable UUID approvalId, @Valid @RequestBody ApprovalDecisionRequest request) {
        ChatApprovalResponse approval = approvalService.decide(approvalId, ApprovalStatus.valueOf(request.decision()),
                UserContextHolder.currentUserId());
        return ApiResponse.success("Decision saved", approval);
    }

    @Operation(summary = "Delete a conversation", description = "Deletes all its messages (for example when the customer clicks 'New chat').")
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(@PathVariable UUID conversationId) {
        conversationService.deleteConversation(conversationId, UserContextHolder.currentUserId());
        return ApiResponse.success("Conversation deleted");
    }
}
