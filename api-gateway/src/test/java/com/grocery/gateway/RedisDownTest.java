package com.grocery.gateway;

import com.grocery.gateway.support.FakeDownstreamService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis goes down while the gateway is running. The rate limiter then lets requests through ("fails open"), and it
 * must do so at once: without a Redis timeout, the Redis client keeps each command waiting for a reconnect (up to
 * 60 s), so every request through the gateway would hang. Redis runs in its own container here, so this test can
 * stop it without disturbing the other tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"RATE_LIMIT_REQUESTS_PER_SECOND=1000", "RATE_LIMIT_BURST=1000"})
class RedisDownTest {

    // With the same Redis user as infrastructure/docker-compose.yml (and the gateway's default password), so this test
    // also proves that the "gateway" user may run the rate limiter's script
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.2-alpine")).withExposedPorts(6379)
                    .withCommand("redis-server --user default off --user gateway on >gateway_pass ~request_rate_limiter.* "
                            + "-@all +@connection +info +evalsha +eval +get +setex +time");   // one string: split at the spaces
    private static final FakeDownstreamService STORE_SERVICE = FakeDownstreamService.start("store-service");

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisAndRoutes(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("STORE_SERVICE_URL", STORE_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopFakeServices() {
        STORE_SERVICE.close();
    }

    @LocalServerPort
    private int port;

    @Test
    void whenRedisGoesDown_requestsStillPass_withinTwoSeconds() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(90))   // long enough to measure how long it really takes
                .build();
        client.get().uri("/api/v1/stores").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "999");   // counted in Redis (-1 would mean "Redis failed")

        REDIS.stop();
        long start = System.nanoTime();
        client.get().uri("/api/v1/stores").exchange().expectStatus().isOk();
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        System.out.println("Request while Redis is down took " + took.toMillis() + " ms");
        assertThat(took).isLessThan(Duration.ofSeconds(2));
    }
}
