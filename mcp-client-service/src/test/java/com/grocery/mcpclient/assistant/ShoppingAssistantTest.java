package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import com.grocery.mcpclient.tool.RecordingToolCallback;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ShoppingAssistant with a real ChatClient (and its tool-calling loop) in front of a fake chat model,
 * so we can see exactly what is sent to the model, without Ollama.
 */
class ShoppingAssistantTest {

    /** 20:00 in India on 26 Oct 2026 (14:30 UTC). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T14:30:00Z"), ZoneOffset.UTC);

    private final FakeChatModel model = new FakeChatModel();
    private final ShoppingAssistant assistant = new ShoppingAssistant(ChatClient.builder(model).build(),
            new SystemPrompt("Rules. Today is {today}. {customer}", "Panel rules. {user} Today is {today}."), properties(), CLOCK);

    @Test
    void sendsTheRulesTheHistoryAndTheQuestionInThisOrder() {
        model.answers.add(answer("Hello! How can I help you?"));
        List<Message> history = List.of(new UserMessage("Hi"), new AssistantMessage("Hello!"));

        String answer = assistant.answer(history, "Do you have milk?", List.of(), Audience.GUEST, null);

        assertThat(answer).isEqualTo("Hello! How can I help you?");
        List<Message> sent = model.prompts.getFirst().getInstructions();
        assertThat(sent).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(sent.getFirst().getText()).startsWith("Rules. Today is Monday, 26 October 2026. The customer is not logged in");
        assertThat(sent.getLast().getText()).isEqualTo("Do you have milk?");
    }

    @Test
    void aVendorGetsThePanelRulesWithTheVendorPart() {
        model.answers.add(answer("You have 3 new orders."));

        assistant.answer(List.of(), "Do I have new orders?", List.of(), Audience.VENDOR, null);

        String rules = model.prompts.getFirst().getInstructions().getFirst().getText();
        assertThat(rules).startsWith("Panel rules. You are talking to a vendor")
                .contains("getVendorStoreToday")
                .endsWith("Today is Monday, 26 October 2026.");
    }

    @Test
    void theCorrectionOfASecondAttemptIsTheLastMessage() {
        model.answers.add(answer("Amul Butter costs ₹56."));

        assistant.answer(List.of(), "Price of butter?", List.of(), Audience.CUSTOMER, "Your first answer was rejected because: ₹60 is unknown.");

        List<Message> sent = model.prompts.getFirst().getInstructions();
        assertThat(sent.getLast().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(sent.getLast().getText()).startsWith("Your first answer was rejected");
    }

    @Test
    void runsTheToolTheModelAsksForAndGivesItTheResult() {
        McpSyncClient mcpClient = mock(McpSyncClient.class);
        when(mcpClient.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .addTextContent("{\"status\":\"OK\",\"data\":[{\"name\":\"Amul Butter\",\"price\":\"₹56\"}]}").isError(false).build());
        McpSchema.Tool searchProducts = McpSchema.Tool.builder().name("searchProducts").description("Search products")
                .inputSchema(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")))).build();
        ToolCallRecorder recorder = new ToolCallRecorder();

        model.answers.add(toolCall("searchProducts", "{\"query\":\"amul butter\"}"));
        model.answers.add(answer("Amul Butter costs ₹56."));

        String answer = assistant.answer(List.of(), "How much is Amul butter?",
                List.of(new RecordingToolCallback(mcpClient, searchProducts, recorder)), Audience.GUEST, null);

        assertThat(answer).isEqualTo("Amul Butter costs ₹56.");
        assertThat(recorder.toolNames()).containsExactly("searchProducts");
        // The second model call gets the tool result
        Message toolResult = model.prompts.get(1).getInstructions().getLast();
        assertThat(toolResult).isInstanceOf(ToolResponseMessage.class);
        assertThat(((ToolResponseMessage) toolResult).getResponses().getFirst().responseData()).contains("₹56");
        // The model was told about the tool
        ToolCallingChatOptions options = (ToolCallingChatOptions) model.prompts.getFirst().getOptions();
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("searchProducts");
    }

    @Test
    void aFailingModelMakesTheAssistantUnavailable() {
        // no answer queued: the fake model throws, like Ollama when it is down

        assertThatThrownBy(() -> assistant.answer(List.of(), "Hi", List.of(), Audience.GUEST, null))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage(AssistantUnavailableException.MESSAGE);
    }

    private static ChatProperties properties() {
        return new ChatProperties(new ByteArrayResource(new byte[0]), new ByteArrayResource(new byte[0]), 10, 2,
                Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofDays(30), ZoneId.of("Asia/Kolkata"), false);
    }

    private static AssistantMessage answer(String text) {
        return new AssistantMessage(text);
    }

    private static AssistantMessage toolCall(String toolName, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", toolName, arguments)))
                .build();
    }

    /** Returns the queued answers one by one and remembers every prompt it received. */
    private static class FakeChatModel implements ChatModel {

        final Queue<AssistantMessage> answers = new LinkedList<>();
        final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            AssistantMessage next = answers.poll();
            if (next == null) {
                throw new IllegalStateException("Connection refused");
            }
            return new ChatResponse(List.of(new Generation(next)));
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }
    }
}
