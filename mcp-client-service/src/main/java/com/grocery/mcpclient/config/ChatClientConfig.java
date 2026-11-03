package com.grocery.mcpclient.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two Spring AI objects the assistant works with.
 *
 * - ChatClient: talks to the chat model (Ollama, settings under spring.ai.ollama.chat). When the model asks for a
 *   tool, ChatClient's ToolCallingAdvisor calls it and sends the result back to the model, until the model answers.
 * - ChatMemory: the conversation history in MySQL (JdbcChatMemoryRepository, table SPRING_AI_CHAT_MEMORY). It keeps
 *   only the last app.chat.history-messages messages of each conversation, so prompts stay small and fast.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, ChatProperties chatProperties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(chatProperties.historyMessages())
                .build();
    }
}
