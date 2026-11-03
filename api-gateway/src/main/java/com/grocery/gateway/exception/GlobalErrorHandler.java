package com.grocery.gateway.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

/**
 * FALLBACK: the one place that answers when the gateway could not get an answer from a service.
 * Every answer has the standard ApiResponse shape and a message for people - never an exception text or a service name.
 *
 * <pre>
 * What happened                                     Status  Retry-After                Logged
 * circuit breaker OPEN (the service failed often)   503     wait of the open circuit   DEBUG (the opening itself: WARN)
 * bulkhead full (too many requests in progress)     503     1 s                        INFO
 * service not reachable (refused, reset, no DNS)    503     wait of the open circuit   WARN
 * service too slow (response timeout)               504     -                          WARN
 * no route for the path                             404     -                          -
 * anything else                                     500     -                          ERROR with stack trace
 * </pre>
 *
 * Graceful degradation: a GET on a path of app.fallback.empty-list-paths (decoration such as banners) gets
 * 200 with an empty list instead of 503 / 504, marked with the header X-Fallback: empty-list.
 *
 * Errors returned BY a service (its own 404, 409, 503 ...) are passed through unchanged; they never come here.
 * Every fallback answer is counted in the metric gateway_fallback_total{reason, route} (Grafana "Grocery services").
 */
@Slf4j
@Component
@Order(-2)   // runs before Spring Boot's default error handler (order -1)
public class GlobalErrorHandler implements WebExceptionHandler {

    static final String UNAVAILABLE_MESSAGE = "The service is temporarily unavailable. Please try again in a moment.";
    static final String BUSY_MESSAGE = "The service is busy right now. Please try again in a moment.";
    static final String TIMEOUT_MESSAGE = "The service took too long to respond. Please try again.";
    static final String DEGRADED_MESSAGE = "Shown without this content: the service is temporarily unavailable.";
    private static final long BUSY_RETRY_AFTER_SECONDS = 1;
    private static final long DEFAULT_RETRY_AFTER_SECONDS = 15;

    private final ErrorResponseWriter errorResponseWriter;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final List<PathPattern> emptyListPaths;

    public GlobalErrorHandler(ErrorResponseWriter errorResponseWriter, CircuitBreakerRegistry circuitBreakerRegistry,
                              MeterRegistry meterRegistry, FallbackProperties fallbackProperties) {
        this.errorResponseWriter = errorResponseWriter;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.emptyListPaths = fallbackProperties.emptyListPaths().stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpRequest request = exchange.getRequest();
        String route = routeId(exchange);

        if (ex instanceof ServiceUnavailableException) {
            // The circuit breaker of this route is OPEN: the service was not even called
            log.debug("Route {}: circuit open, {} {} answered 503 at once", route, request.getMethod(), request.getPath());
            count("circuit_open", route);
            return serviceUnavailable(exchange, UNAVAILABLE_MESSAGE, retryAfterOfOpenCircuit(route));
        }
        if (ex instanceof BulkheadFullException) {
            // Throttling: this gateway instance already has the maximum number of requests in progress for the route
            log.info("Route {}: busy, {} {} rejected (bulkhead full)", route, request.getMethod(), request.getPath());
            count("busy", route);
            return serviceUnavailable(exchange, BUSY_MESSAGE, BUSY_RETRY_AFTER_SECONDS);
        }
        if (isConnectionFailure(ex)) {
            // The service is not running, crashed, or cannot be found (after the retries for GET requests)
            log.warn("Route {}: {} {} failed, service not reachable: {}", route, request.getMethod(), request.getPath(), rootCause(ex));
            count("unreachable", route);
            return serviceUnavailable(exchange, UNAVAILABLE_MESSAGE, retryAfterOfOpenCircuit(route));
        }

        HttpStatus status = statusOf(ex);
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            log.warn("Route {}: {} {} timed out: {}", route, request.getMethod(), request.getPath(), rootCause(ex));
            count("timeout", route);
            return isDegradable(request) ? emptyList(exchange) : errorResponseWriter.write(exchange, status, TIMEOUT_MESSAGE);
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Unexpected error on {} {} (route {})", request.getMethod(), request.getPath(), route, ex);
        }
        return errorResponseWriter.write(exchange, status, messageFor(status, request));
    }

    /** 503 with Retry-After, or the empty-list answer for decoration. */
    private Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message, long retryAfterSeconds) {
        if (isDegradable(exchange.getRequest())) {
            return emptyList(exchange);
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private boolean isDegradable(ServerHttpRequest request) {
        return HttpMethod.GET.equals(request.getMethod())
                && emptyListPaths.stream().anyMatch(pattern -> pattern.matches(request.getPath().pathWithinApplication()));
    }

    private Mono<Void> emptyList(ServerWebExchange exchange) {
        log.warn("Fallback: {} answered with an empty list (graceful degradation)", exchange.getRequest().getPath());
        count("degraded", routeId(exchange));
        exchange.getResponse().getHeaders().set("X-Fallback", "empty-list");
        return errorResponseWriter.writeFallback(exchange, DEGRADED_MESSAGE, List.of());
    }

    private void count(String reason, String route) {
        meterRegistry.counter("gateway.fallback", "reason", reason, "route", route).increment();
    }

    /** How long the route's circuit breaker stays open: a good moment for the client to try again. */
    private long retryAfterOfOpenCircuit(String routeId) {
        return circuitBreakerRegistry.find(routeId)
                .map(breaker -> breaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .map(millis -> Math.max(1, millis / 1000))
                .orElse(DEFAULT_RETRY_AFTER_SECONDS);
    }

    /** Connection refused, reset, closed early, unknown host ...: an IOException somewhere in the cause chain. */
    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static HttpStatus statusOf(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String messageFor(HttpStatus status, ServerHttpRequest request) {
        return switch (status) {
            case NOT_FOUND -> "No route for %s %s".formatted(request.getMethod(), request.getPath());
            case SERVICE_UNAVAILABLE -> UNAVAILABLE_MESSAGE;
            case INTERNAL_SERVER_ERROR -> "Something went wrong. Please try again later.";
            default -> status.getReasonPhrase();
        };
    }

    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "none";
    }

    private static String rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }
}
