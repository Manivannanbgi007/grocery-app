package com.grocery.gateway;

import com.grocery.gateway.support.FakeDownstreamService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The shopping assistant has its own, stricter limit for questions (policy "chat"). Here its burst is 1, so the second
 * question at once gets 429, while the default limit (set very high) is not the reason.
 * Reading a conversation is not a question and is not limited by the chat limit. Voice has a limit of its own ("voice").
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"RATE_LIMIT_REQUESTS_PER_SECOND=1000", "RATE_LIMIT_BURST=1000",
                "RATE_LIMIT_CHAT_BURST=1", "RATE_LIMIT_VOICE_BURST=1"})
@Import(TestcontainersConfiguration.class)
class ChatRateLimitTest {

    private static final FakeDownstreamService MCP_CLIENT_SERVICE = FakeDownstreamService.start("mcp-client-service");

    @DynamicPropertySource
    static void routeToFakeService(DynamicPropertyRegistry registry) {
        registry.add("MCP_CLIENT_SERVICE_URL", MCP_CLIENT_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopFakeService() {
        MCP_CLIENT_SERVICE.close();
    }

    @LocalServerPort
    private int port;

    @Test
    void tooManyQuestions_getA429_butReadingConversationsStillWorks() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        askAQuestion(client).expectStatus().isOk();
        askAQuestion(client).expectStatus().isEqualTo(429);

        client.get().uri("/api/v1/chat/conversations/3f0c8a8e-2d6b-4c1e-9a43-8f1f4f3c2b10")
                .exchange()
                .expectStatus().isOk();
    }

    /** Voice (transcription and speech) has its own limit "voice" (here burst 1): one budget for both endpoints. */
    @Test
    void voiceHasItsOwnLimit_sharedByTranscriptionAndSpeech() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        readAloud(client).expectStatus().isOk();
        readAloud(client).expectStatus().isEqualTo(429);
        client.post().uri("/api/v1/chat/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(new MultipartBodyBuilder().build())
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    private static WebTestClient.ResponseSpec readAloud(WebTestClient client) {
        return client.post().uri("/api/v1/chat/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\": \"Hello\"}")
                .exchange();
    }

    private static WebTestClient.ResponseSpec askAQuestion(WebTestClient client) {
        return client.post().uri("/api/v1/chat/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"Do you have milk?\"}")
                .exchange();
    }
}
