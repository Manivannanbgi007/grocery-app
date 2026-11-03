package com.grocery.gateway.ratelimit;

import com.grocery.gateway.exception.ErrorResponseWriter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * RATE LIMITING: how many requests ONE client may send (app.rate-limit in application.yml).
 *
 * For every request that has a route: find the policy (login, chat, or default), find the client (ClientKeyResolver),
 * and ask Redis whether the client has a token left. Yes -> the request goes on. No -> 429 Too Many Requests with a
 * Retry-After header, in the standard ApiResponse shape, and the request never reaches the service.
 *
 * The counting itself is Spring Cloud Gateway's RedisRateLimiter: a "token bucket" in Redis (a Lua script, so it is
 * exact even with many gateway instances). Each client has a bucket per policy that refills at the policy's rate and
 * holds at most "burst" requests. If Redis is down, the RedisRateLimiter lets requests through ("fails open"): the
 * shop keeps working without limits instead of failing completely; it logs an error for every such request.
 *
 * Runs before the circuit breaker and the retry (order HIGHEST_PRECEDENCE + 10), so rejected requests cost nothing.
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /** One allowed request per minute = 60 tokens, refilled at "requests per minute" tokens per second. */
    private static final int TOKENS_PER_REQUEST_PER_MINUTE = 60;

    private final RedisRateLimiter redisRateLimiter;
    private final ClientKeyResolver clientKeyResolver;
    private final ErrorResponseWriter errorResponseWriter;
    private final MeterRegistry meterRegistry;
    private final List<Limit> policies = new ArrayList<>();
    private final Limit defaultPolicy;

    /**
     * A policy ready to use: its path patterns parsed, and its Redis token-bucket settings.
     *
     * @param tokensPerRequest  what one request costs
     * @param retryAfterSeconds how long until a rejected client has a token again
     */
    record Limit(RateLimitProperties.Policy policy, List<PathPattern> pathPatterns, int tokensPerRequest,
                 long retryAfterSeconds) {

        boolean matches(ServerHttpRequest request) {
            boolean methodMatches = policy.methods().isEmpty() || policy.methods().contains(request.getMethod());
            PathContainer path = request.getPath().pathWithinApplication();
            return methodMatches && pathPatterns.stream().anyMatch(pattern -> pattern.matches(path));
        }
    }

    public RateLimitFilter(RateLimitProperties properties, RedisRateLimiter redisRateLimiter,
                           ClientKeyResolver clientKeyResolver, ErrorResponseWriter errorResponseWriter,
                           MeterRegistry meterRegistry) {
        this.redisRateLimiter = redisRateLimiter;
        this.clientKeyResolver = clientKeyResolver;
        this.errorResponseWriter = errorResponseWriter;
        this.meterRegistry = meterRegistry;
        this.defaultPolicy = register(properties.defaultPolicy());
        properties.policies().forEach(policy -> policies.add(register(policy)));
    }

    /**
     * Translates "N requests per second / minute, burst B" into token-bucket settings and gives them to the
     * RedisRateLimiter under the policy's name.
     *   per second: 1 token per request, N tokens refilled per second, at most B tokens saved up
     *   per minute: 60 tokens per request, N tokens refilled per second (= N requests per minute), at most B x 60
     */
    private Limit register(RateLimitProperties.Policy policy) {
        boolean perSecond = policy.requestsPerSecond() != null;
        int tokensPerRequest = perSecond ? 1 : TOKENS_PER_REQUEST_PER_MINUTE;
        int refillPerSecond = perSecond ? policy.requestsPerSecond() : policy.requestsPerMinute();

        redisRateLimiter.getConfig().put(policy.name(), new RedisRateLimiter.Config()
                .setReplenishRate(refillPerSecond)
                .setBurstCapacity((long) policy.burst() * tokensPerRequest)
                .setRequestedTokens(tokensPerRequest));

        long retryAfterSeconds = Math.max(1, (long) Math.ceil((double) tokensPerRequest / refillPerSecond));
        List<PathPattern> patterns = policy.paths().stream().map(PathPatternParser.defaultInstance::parse).toList();
        log.info("Rate limit '{}': {} per {}, burst {}{}", policy.name(), refillPerSecond, perSecond ? "second" : "minute",
                policy.burst(), patterns.isEmpty() ? "" : " for " + policy.methods() + " " + policy.paths());
        return new Limit(policy, patterns, tokensPerRequest, retryAfterSeconds);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Limit limit = limitFor(exchange.getRequest());
        String policyName = limit.policy().name();

        return clientKeyResolver.resolve(exchange)
                .flatMap(client -> redisRateLimiter.isAllowed(policyName, client)
                        .flatMap(response -> {
                            HttpHeaders headers = exchange.getResponse().getHeaders();
                            headers.set("X-RateLimit-Policy", policyName);
                            String remainingTokens = response.getHeaders().get(RedisRateLimiter.REMAINING_HEADER);
                            if (remainingTokens != null && !remainingTokens.startsWith("-")) {
                                long remainingRequests = Long.parseLong(remainingTokens) / limit.tokensPerRequest();
                                headers.set("X-RateLimit-Remaining", String.valueOf(remainingRequests));
                            }
                            if (response.isAllowed()) {
                                return chain.filter(exchange);
                            }
                            return reject(exchange, limit, client);
                        }));
    }

    private Mono<Void> reject(ServerWebExchange exchange, Limit limit, String client) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("Rate limit '{}' reached by {} on {} {}", limit.policy().name(), client, request.getMethod(),
                request.getPath());
        meterRegistry.counter("gateway.rate.limited", "policy", limit.policy().name()).increment();

        long seconds = limit.retryAfterSeconds();
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        return errorResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please wait %d %s and try again.".formatted(seconds, seconds == 1 ? "second" : "seconds"));
    }

    private Limit limitFor(ServerHttpRequest request) {
        return policies.stream().filter(limit -> limit.matches(request)).findFirst().orElse(defaultPolicy);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;   // after UserHeadersFilter, before every route filter
    }
}
