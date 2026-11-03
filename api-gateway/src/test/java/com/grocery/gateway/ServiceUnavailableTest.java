package com.grocery.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/**
 * When the target service is not running, the client gets a clear 503 in the standard shape
 * instead of a raw connection error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ServiceUnavailableTest {

    @DynamicPropertySource
    static void routeToStoppedService(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> "http://localhost:" + portWithNothingListening());
    }

    @LocalServerPort
    private int port;

    @Test
    void stoppedService_returns503InStandardShape() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("The service is temporarily unavailable. Please try again in a moment.")
                .jsonPath("$.path").isEqualTo("/api/v1/auth/login");
    }

    /** Opens a free port and closes it again, so nothing is listening there. */
    private static int portWithNothingListening() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
