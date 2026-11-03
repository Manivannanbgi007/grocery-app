package com.grocery.mcpclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole service with a real MySQL, while Ollama and mcp-server-service cannot be reached (port 1):
 * it must start anyway (the MCP client connects on first use) and answer 503 quickly instead of hanging.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.mcp.client.streamable-http.connections.grocery-tools.url=http://localhost:1",
        "spring.ai.ollama.base-url=http://localhost:1",
        "app.chat.warm-up=false"
})
@Import(TestcontainersConfiguration.class)
class McpClientServiceApplicationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void startsAndIsHealthyWithoutTheModelAndTheTools() {
        ResponseEntity<String> health = client().get().uri("/actuator/health").retrieve().toEntity(String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void answers503QuicklyWhenTheToolsCannotBeReached() {
        Instant start = Instant.now();

        ResponseEntity<String> response = client().post().uri("/api/v1/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\": \"Do you have milk?\"}")
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> { })   // no exception for 4xx/5xx
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("The assistant is not available right now");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(10));
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
