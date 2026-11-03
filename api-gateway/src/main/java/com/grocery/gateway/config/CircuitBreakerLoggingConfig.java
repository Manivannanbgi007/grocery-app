package com.grocery.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Logs every state change of a route's circuit breaker - once per change, not once per rejected request:
 *
 * <pre>
 * WARN  Circuit breaker 'cart-service' is OPEN for 15 s: requests are answered 503 without calling the service (failure rate 60.0 %)
 * INFO  Circuit breaker 'cart-service' is HALF_OPEN: a few test requests go through
 * INFO  Circuit breaker 'cart-service' is CLOSED again: the service answers normally
 * </pre>
 *
 * The circuit breakers are created when a route is used for the first time, so the logging is attached to each new
 * one as soon as the registry adds it. (The current states are also metrics: resilience4j_circuitbreaker_state.)
 */
@Slf4j
@Configuration
public class CircuitBreakerLoggingConfig {

    @Bean
    SmartInitializingSingleton circuitBreakerStateLogging(CircuitBreakerRegistry registry) {
        return () -> {
            registry.getAllCircuitBreakers().forEach(CircuitBreakerLoggingConfig::logStateChanges);
            registry.getEventPublisher().onEntryAdded(event -> logStateChanges(event.getAddedEntry()));
        };
    }

    private static void logStateChanges(CircuitBreaker breaker) {
        breaker.getEventPublisher().onStateTransition(event -> {
            String name = breaker.getName();
            switch (event.getStateTransition().getToState()) {
                case OPEN -> log.warn("Circuit breaker '{}' is OPEN for {} s: requests are answered 503 without calling "
                                + "the service (failure rate {} %)", name, waitSeconds(breaker),
                        breaker.getMetrics().getFailureRate());
                case HALF_OPEN -> log.info("Circuit breaker '{}' is HALF_OPEN: a few test requests go through", name);
                case CLOSED -> log.info("Circuit breaker '{}' is CLOSED again: the service answers normally", name);
                default -> log.info("Circuit breaker '{}' changed: {}", name, event.getStateTransition());
            }
        });
    }

    private static long waitSeconds(CircuitBreaker breaker) {
        return breaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1) / 1000;
    }
}
