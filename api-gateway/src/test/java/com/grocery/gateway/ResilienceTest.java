package com.grocery.gateway;

import com.grocery.gateway.support.ControllableDownstreamService;
import com.grocery.gateway.support.TestTokens;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retry, timeout, circuit breaker, throttling (bulkhead) and fallback of the gateway, against a fake service whose
 * behaviour each test sets. Small values make the test fast: response timeout 1 s, the circuit opens after 4 calls
 * with 50 % failures and stays open for 2 s, at most 1 request in progress per route.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "RATE_LIMIT_REQUESTS_PER_SECOND=1000", "RATE_LIMIT_BURST=1000",
                "GATEWAY_RESPONSE_TIMEOUT=1s",
                "GATEWAY_CB_WINDOW_SIZE=4", "GATEWAY_CB_MINIMUM_CALLS=4", "GATEWAY_CB_WAIT_IN_OPEN_STATE=2s",
                "GATEWAY_MAX_CONCURRENT_CALLS=1"})
@Import(TestcontainersConfiguration.class)
class ResilienceTest {

    private static final ControllableDownstreamService SERVICE = ControllableDownstreamService.start();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("STORE_SERVICE_URL", SERVICE::baseUrl);        // GET  /api/v1/stores
        registry.add("CART_SERVICE_URL", SERVICE::baseUrl);         // POST /api/v1/cart/items
        registry.add("DELIVERY_SERVICE_URL", SERVICE::baseUrl);     // GET  /api/v1/delivery-tracking/...
        registry.add("ORDER_SERVICE_URL", SERVICE::baseUrl);        // GET  /api/v1/vendor/orders/...
        registry.add("PLATFORM_SERVICE_URL", () -> "http://localhost:" + portWithNothingListening());
    }

    @AfterAll
    static void stopService() {
        SERVICE.close();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @BeforeEach
    void freshStart() {
        SERVICE.reset();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ----- Retry -----

    @Test
    void aGetIsRetriedAfterATemporaryFailure() {
        SERVICE.failFirst(2, 503);

        client().get().uri("/api/v1/stores").exchange().expectStatus().isOk();

        assertThat(SERVICE.requestCount()).isEqualTo(3);   // 2 failures + the successful retry
    }

    @Test
    void aPostIsNeverRetried_andTheServicesOwnErrorIsPassedOn() {
        SERVICE.respondWith(503);

        client().post().uri("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo("downstream answered 503");   // the service's own answer

        assertThat(SERVICE.requestCount()).isEqualTo(1);   // a second POST could add the item twice
    }

    @Test
    void a500IsAnAnswerOfTheService_notRetriedAndPassedOn() {
        SERVICE.respondWith(500);

        client().get().uri("/api/v1/stores").exchange()
                .expectStatus().isEqualTo(500)
                .expectBody().jsonPath("$.message").isEqualTo("downstream answered 500");

        assertThat(SERVICE.requestCount()).isEqualTo(1);
    }

    // ----- Timeout -----

    @Test
    void aSlowServiceGets504_andTheRequestIsNotRepeated() {
        SERVICE.respondAfter(Duration.ofSeconds(3));

        client().get().uri("/api/v1/stores").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.message").isEqualTo("The service took too long to respond. Please try again.");

        assertThat(SERVICE.requestCount()).isEqualTo(1);
    }

    // ----- Circuit breaker, and recovery -----

    @Test
    void theCircuitOpensAfterRepeatedFailures_answers503AtOnce_andClosesWhenTheServiceIsBack() throws InterruptedException {
        SERVICE.respondAfter(Duration.ofSeconds(3));   // every call times out = a failure
        for (int i = 0; i < 4; i++) {
            client().get().uri("/api/v1/stores").exchange().expectStatus().isEqualTo(504);
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.OPEN);

        // OPEN: answered at once, the service is not called
        int callsBefore = SERVICE.requestCount();
        client().get().uri("/api/v1/stores").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "2")
                .expectBody().jsonPath("$.message").isEqualTo("The service is temporarily unavailable. Please try again in a moment.");
        assertThat(SERVICE.requestCount()).isEqualTo(callsBefore);

        // The service recovers; after the wait the circuit lets test requests through and closes again
        SERVICE.reset();
        Thread.sleep(2_500);
        for (int i = 0; i < 3; i++) {
            client().get().uri("/api/v1/stores").exchange().expectStatus().isOk();
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ----- Throttling (bulkhead) -----

    @Test
    void whenTooManyRequestsAreInProgress_theExtraOneGets503Busy() {
        SERVICE.respondAfter(Duration.ofMillis(700));
        WebClient webClient = WebClient.create("http://localhost:" + port);

        List<Integer> statuses = Flux.merge(status(webClient), status(webClient)).collectList().block();

        assertThat(statuses).containsExactlyInAnyOrder(200, 503);
    }

    // ----- Live tracking stream: a request that stays open on purpose -----

    @Test
    void theTrackingStream_isNotCutByTheResponseTimeout_andHasItsOwnLimits() {
        SERVICE.respondAfter(Duration.ofMillis(1500));   // slower than this test's 1 s response timeout

        client().get().uri("/api/v1/delivery-tracking/orders/18")             // the snapshot: a normal request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .exchange().expectStatus().isEqualTo(504);
        client().get().uri("/api/v1/delivery-tracking/orders/18/stream")      // the stream: no timeout
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .exchange().expectStatus().isOk();

        CircuitBreaker streamBreaker = circuitBreakerRegistry.circuitBreaker("delivery-service-tracking-stream");
        assertThat(streamBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);   // the route uses this breaker
        assertThat(streamBreaker.getCircuitBreakerConfig().getSlowCallDurationThreshold()).isEqualTo(Duration.ofHours(2));
        assertThat(bulkheadRegistry.bulkhead("delivery-service-tracking-stream").getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(2000);                         // not the 1 of every other route in this test
    }

    @Test
    void theVendorOrderStream_isNotCutByTheResponseTimeout_andHasItsOwnLimits() {
        SERVICE.respondAfter(Duration.ofMillis(1500));   // slower than this test's 1 s response timeout

        client().get().uri("/api/v1/vendor/orders")                            // the queue: a normal request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.vendor())
                .exchange().expectStatus().isEqualTo(504);
        client().get().uri("/api/v1/vendor/orders/stream")                     // the live stream: no timeout
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.vendor())
                .exchange().expectStatus().isOk();

        CircuitBreaker streamBreaker = circuitBreakerRegistry.circuitBreaker("order-service-vendor-order-stream");
        assertThat(streamBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);   // the route uses this breaker
        assertThat(streamBreaker.getCircuitBreakerConfig().getSlowCallDurationThreshold()).isEqualTo(Duration.ofHours(2));
        assertThat(bulkheadRegistry.bulkhead("order-service-vendor-order-stream").getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(2000);
    }

    // ----- Fallback: graceful degradation only for decoration -----

    @Test
    void bannersDegradeToAnEmptyList_butSettingsAnswerAnHonest503() {
        // platform-service is not running
        client().get().uri("/api/v1/banners").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Fallback", "empty-list")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isEmpty()
                .jsonPath("$.message").isEqualTo("Shown without this content: the service is temporarily unavailable.");

        client().get().uri("/api/v1/platform/settings").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists("Retry-After");
    }

    private CircuitBreaker.State state() {
        return circuitBreakerRegistry.circuitBreaker("store-service").getState();
    }

    private static Mono<Integer> status(WebClient webClient) {
        return webClient.get().uri("/api/v1/stores").exchangeToMono(response -> Mono.just(response.statusCode().value()));
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(10)).build();
    }

    private static int portWithNothingListening() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
