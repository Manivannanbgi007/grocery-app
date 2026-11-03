package com.grocery.mcpclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The AI shopping assistant (chatbot backend).
 *
 * POST /api/v1/chat/messages: the question goes to a language model (Ollama, Qwen) together with the tools of
 * mcp-server-service (MCP). The model calls the tools it needs, and the answer is checked against the tool results
 * before it is returned. Conversations are kept in MySQL (chat_db).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpClientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientServiceApplication.class, args);
    }
}
