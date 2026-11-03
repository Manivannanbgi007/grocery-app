package com.grocery.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * MCP server of the AI shopping assistant.
 *
 * Offers read-only tools (search products, offers, delivery rules, stores ...) over the Model Context Protocol
 * at POST /mcp. Only mcp-client-service calls it, with an API key; every tool reads its data through the API gateway.
 *
 * UserDetailsServiceAutoConfiguration is excluded: there are no users or passwords here, only the API key
 * (otherwise Spring Boot would create a default user and print a generated password).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class McpServerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerServiceApplication.class, args);
    }
}
