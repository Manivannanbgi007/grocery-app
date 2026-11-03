package com.grocery.gateway;

import com.grocery.gateway.support.FakeDownstreamService;
import com.grocery.gateway.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Rate limiting per client (RateLimitFilter), counted in a real Redis (Testcontainers):
 * the default policy is set to 1 request per second without burst, the login policy to 1 per minute.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"RATE_LIMIT_REQUESTS_PER_SECOND=1", "RATE_LIMIT_BURST=1",
                "RATE_LIMIT_LOGIN_PER_MINUTE=1", "RATE_LIMIT_LOGIN_BURST=1"})
@Import(TestcontainersConfiguration.class)
class RateLimitTest {

    private static final FakeDownstreamService STORE_SERVICE = FakeDownstreamService.start("store-service");
    private static final FakeDownstreamService AUTH_SERVICE = FakeDownstreamService.start("auth-service");

    @DynamicPropertySource
    static void routeToFakeServices(DynamicPropertyRegistry registry) {
        registry.add("STORE_SERVICE_URL", STORE_SERVICE::baseUrl);
        registry.add("AUTH_SERVICE_URL", AUTH_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopFakeServices() {
        STORE_SERVICE.close();
        AUTH_SERVICE.close();
    }

    @LocalServerPort
    private int port;

    @Test
    void tooManyRequests_get429InTheStandardShapeWithRetryAfter() throws InterruptedException {
        WebTestClient client = client();
        // The rate limiter counts in whole seconds (Redis TIME): two requests on both sides of a second boundary both
        // pass, however close they are. Start right after a second begins, so both fall into the same second.
        while (System.currentTimeMillis() % 1000 > 200) {
            Thread.sleep(5);
        }

        client.get().uri("/api/v1/stores").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Policy", "default")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");

        client.get().uri("/api/v1/stores").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "1")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Too many requests. Please wait 1 second and try again.")
                .jsonPath("$.path").isEqualTo("/api/v1/stores");

        // Every client has its own limit: a logged-in user is not stopped by the guest's used-up limit
        client.get().uri("/api/v1/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void loginHasItsOwnStricterLimit_perMinute() {
        WebTestClient client = client();

        login(client).expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Policy", "login");
        login(client).expectStatus().isEqualTo(429).expectHeader().valueEquals("Retry-After", "60")
                .expectBody().jsonPath("$.message").isEqualTo("Too many requests. Please wait 60 seconds and try again.");
    }

    private static WebTestClient.ResponseSpec login(WebTestClient client) {
        return client.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
