package com.grocery.mcpserver.config;

import com.grocery.mcpserver.constant.HeaderNames;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * The MCP endpoint (POST /mcp), as Spring AI would create it, plus one thing: the "context extractor" copies the
 * logged-in user's token (X-User-Token header) into the MCP transport context of each request. The tools for a
 * user's own data (customer, vendor, partner and admin tools) receive that context as a method parameter
 * (McpTransportContext) and read the token from it (see LoginToken).
 *
 * With this bean, Spring AI's own WebMvcStatelessServerTransport is not created (it is @ConditionalOnMissingBean).
 */
@Configuration
public class McpTransportConfig {

    /** Key of the user's token in the transport context (see LoginToken). */
    public static final String USER_TOKEN = "userToken";

    @Bean
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> request.headers().firstHeader(HeaderNames.USER_TOKEN) instanceof String token
                        && StringUtils.hasText(token)
                        ? McpTransportContext.create(Map.of(USER_TOKEN, token))
                        : McpTransportContext.EMPTY)
                .build();
    }
}
