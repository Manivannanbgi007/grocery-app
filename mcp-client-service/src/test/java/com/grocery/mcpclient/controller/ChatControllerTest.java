package com.grocery.mcpclient.controller;

import com.grocery.mcpclient.constant.HeaderNames;
import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.dto.request.ChatMessageRequest;
import com.grocery.mcpclient.dto.response.ChatApprovalResponse;
import com.grocery.mcpclient.dto.response.ChatLink;
import com.grocery.mcpclient.dto.response.ChatReplyResponse;
import com.grocery.mcpclient.dto.response.ConversationResponse;
import com.grocery.mcpclient.exception.AssistantBusyException;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import com.grocery.mcpclient.exception.ResourceNotFoundException;
import com.grocery.mcpclient.entity.ApprovalStatus;
import com.grocery.mcpclient.service.ApprovalService;
import com.grocery.mcpclient.service.ChatService;
import com.grocery.mcpclient.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("3f0c8a8e-2d6b-4c1e-9a43-8f1f4f3c2b10");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;
    @MockitoBean
    private ConversationService conversationService;
    @MockitoBean
    private ApprovalService approvalService;

    @Test
    void aGuestGetsTheAnswer() throws Exception {
        when(chatService.sendMessage(any(), eq(UserContext.GUEST))).thenReturn(
                new ChatReplyResponse(CONVERSATION_ID, "Amul Butter costs ₹56.", true, List.of("searchProducts"),
                        List.of(new ChatLink("Amul Butter", "/products/12"))));

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"How much is Amul butter?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.data.answer").value("Amul Butter costs ₹56."))
                .andExpect(jsonPath("$.data.grounded").value(true))
                .andExpect(jsonPath("$.data.toolsUsed[0]").value("searchProducts"))
                .andExpect(jsonPath("$.data.links[0].label").value("Amul Butter"))
                .andExpect(jsonPath("$.data.links[0].path").value("/products/12"))
                .andExpect(header().exists(HeaderNames.CORRELATION_ID));
    }

    @Test
    void aLoggedInUserIsTakenFromTheGatewayHeader() throws Exception {
        when(chatService.sendMessage(any(), any())).thenReturn(
                new ChatReplyResponse(CONVERSATION_ID, "Hello!", false, List.of()));

        mockMvc.perform(post("/api/v1/chat/messages").header(HeaderNames.USER_ID, "4")
                        .header(HeaderNames.USER_ROLE, "CUSTOMER")
                        .header("Authorization", "Bearer customer-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\": \"" + CONVERSATION_ID + "\", \"message\": \"Hi\"}"))
                .andExpect(status().isOk());

        verify(chatService).sendMessage(new ChatMessageRequest(CONVERSATION_ID, "Hi"),
                new UserContext(4L, "CUSTOMER", "customer-jwt"));
    }

    @Test
    void anEmptyOrTooLongMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.errors[0].message").value("Please type a message"));

        String tooLong = "a".repeat(ChatMessageRequest.MAX_LENGTH + 1);
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("A message can have at most 500 characters"));

        verifyNoInteractions(chatService);
    }

    @Test
    void aWishlistIsCheckedLikeTheMessage() throws Exception {
        String tooMany = "[" + "\"Tomato\",".repeat(ChatMessageRequest.MAX_WISHLIST_ITEMS) + "\"Onion\"]";
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"What is on my wishlist?\", \"wishlist\": " + tooMany + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("wishlist"))
                .andExpect(jsonPath("$.errors[0].message").value("A wishlist can have at most 50 products"));

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"What is on my wishlist?\", \"wishlist\": [\" \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("A wishlist product needs a name"));

        verifyNoInteractions(chatService);
    }

    @Test
    void aConfirmedCardIsOnlyAKnownKindAndAnOrderNumber() throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Cancel it\", \"confirmed\": [{\"kind\": \"PAY_ORDER\", \"orderId\": 207}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("kind must be PLACE_ORDER or CANCEL_ORDER"));

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Cancel it\", \"confirmed\": [{\"kind\": \"PLACE_ORDER\", \"orderId\": -1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("orderId must be a positive number"));

        String tooMany = "[" + "{\"kind\": \"PLACE_ORDER\", \"orderId\": 1},".repeat(ChatMessageRequest.MAX_CONFIRMED)
                + "{\"kind\": \"PLACE_ORDER\", \"orderId\": 2}]";
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Cancel it\", \"confirmed\": " + tooMany + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("At most 5 confirmed cards can be sent"));

        verifyNoInteractions(chatService);
    }

    @Test
    void aConversationIdThatIsNotAUuidIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\": \"42\", \"message\": \"Hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body is missing or invalid (check the JSON syntax; conversationId must be a UUID)"));
    }

    @Test
    void anUnavailableAssistantAnswers503() throws Exception {
        when(chatService.sendMessage(any(), any())).thenThrow(new AssistantUnavailableException(new RuntimeException("Connection refused")));

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"Hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(AssistantUnavailableException.MESSAGE));
    }

    @Test
    void aDatabaseThatDoesNotAnswerIs503_notAnUnexpectedError() throws Exception {
        // As seen under load: the conversation memory's JdbcTemplate got no connection (MySQL read timed out)
        when(chatService.sendMessage(any(), any())).thenThrow(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                new SQLException("Communications link failure")));

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"Hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The service is busy right now. Please try again in a moment."));
    }

    @Test
    void aBusyAssistantAnswers503WithRetryAfter() throws Exception {
        when(chatService.sendMessage(any(), any())).thenThrow(new AssistantBusyException());

        mockMvc.perform(post("/api/v1/chat/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"Hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"));
    }

    @Test
    void theMessagesOfOwnConversationsCanBeReadAndDeleted() throws Exception {
        when(conversationService.getConversation(CONVERSATION_ID, 4L)).thenReturn(new ConversationResponse(CONVERSATION_ID,
                List.of(new ConversationResponse.Line("USER", "Hi"), new ConversationResponse.Line("ASSISTANT", "Hello!"))));

        mockMvc.perform(get("/api/v1/chat/conversations/{id}", CONVERSATION_ID).header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.messages[1].text").value("Hello!"));

        mockMvc.perform(delete("/api/v1/chat/conversations/{id}", CONVERSATION_ID).header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Conversation deleted"));
        verify(conversationService).deleteConversation(CONVERSATION_ID, 4L);
    }

    @Test
    void theCustomersDecisionOnACardIsSaved() throws Exception {
        UUID cardId = UUID.fromString("5d1f0b4e-9f2a-4a51-8f43-0c2b7a1e9d10");
        when(approvalService.decide(cardId, ApprovalStatus.APPROVED, 7L)).thenReturn(new ChatApprovalResponse(cardId.toString(),
                "APPROVED", Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-01T10:01:30Z"), 7L));

        mockMvc.perform(post("/api/v1/chat/approvals/{id}", cardId).header(HeaderNames.USER_ID, "7")
                        .header(HeaderNames.USER_ROLE, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Decision saved"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedAt").value("2026-11-01T10:01:30Z"))
                .andExpect(jsonPath("$.data.decidedByUserId").value(7));
    }

    @Test
    void aDecisionIsApprovedOrRejected_nothingElse() throws Exception {
        mockMvc.perform(post("/api/v1/chat/approvals/{id}", UUID.randomUUID()).header(HeaderNames.USER_ID, "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("decision must be APPROVED or REJECTED"));
        verifyNoInteractions(approvalService);
    }

    @Test
    void someoneElsesConversationIsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Conversation not found."))
                .when(conversationService).deleteConversation(CONVERSATION_ID, null);

        mockMvc.perform(delete("/api/v1/chat/conversations/{id}", CONVERSATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Conversation not found."));
    }
}
