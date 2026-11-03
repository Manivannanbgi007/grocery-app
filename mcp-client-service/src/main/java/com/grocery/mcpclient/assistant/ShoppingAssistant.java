package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks the language model one question (the shop's assistant for guests and customers, the work assistant in the
 * panels: the same model, other rules and tools).
 *
 * What the model receives: the system prompt (the rules, see SystemPrompt), the earlier messages of the
 * conversation, the question, and the tool list. ChatClient runs the tool calls the model asks for and returns the
 * model's final answer. This class knows nothing about checking the answer: that is AnswerValidator's job.
 */
@Component
public class ShoppingAssistant {

    private final ChatClient chatClient;
    private final SystemPrompt systemPrompt;
    private final ChatProperties chatProperties;
    private final Clock clock;

    public ShoppingAssistant(ChatClient chatClient, SystemPrompt systemPrompt, ChatProperties chatProperties, Clock clock) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.chatProperties = chatProperties;
        this.clock = clock;
    }

    /**
     * @param history    earlier messages of the conversation (oldest first)
     * @param question   the user's (cleaned) message
     * @param tools      the MCP tools for this question
     * @param audience   who is chatting: decides the rules (shop or panel, see SystemPrompt)
     * @param correction null on the first attempt; on the second attempt, what was wrong with the first answer
     * @return the model's answer (may still be wrong: check it)
     * @throws AssistantUnavailableException when the model does not answer (Ollama down, model missing, timeout)
     */
    public String answer(List<Message> history, String question, List<ToolCallback> tools, Audience audience,
                         String correction) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(question));
        if (correction != null) {
            // After the question, as the last thing the model reads: small models follow a note there much better
            // than one added to the long system prompt.
            messages.add(new SystemMessage(correction));
        }
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt.render(LocalDate.now(clock.withZone(chatProperties.timeZone())), audience))
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .content();
            return answer == null ? "" : answer.strip();
        } catch (RuntimeException ex) {
            throw new AssistantUnavailableException(ex);
        }
    }
}
