package com.grocery.gateway.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Settings under "app.rate-limit" in application.yml: how many requests one client may send.
 *
 * @param trustedProxies proxies in front of the gateway that add X-Forwarded-For (Kubernetes Ingress: 1; local: 0)
 * @param defaultPolicy  the limit of every request that no policy matches
 * @param policies       stricter limits for some requests; the first matching policy wins
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @Min(0) int trustedProxies,
        @NotNull @Valid Policy defaultPolicy,
        List<@Valid Policy> policies) {

    public static final String DEFAULT_POLICY_NAME = "default";

    public RateLimitProperties {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    /**
     * One limit. Give EITHER requests per second OR requests per minute.
     *
     * @param name              used in the Redis key, the logs and the metric (default: "default")
     * @param methods           HTTP methods it applies to; empty = all methods
     * @param paths             path patterns it applies to, e.g. /api/v1/auth/login or /api/v1/chat/** (not for the default)
     * @param requestsPerSecond for example 20: 20 requests per second
     * @param requestsPerMinute for example 10: 10 requests per minute
     * @param burst             at most this many requests in a row (the client "saves up" unused requests up to here)
     */
    public record Policy(
            String name,
            List<HttpMethod> methods,
            List<String> paths,
            @Min(1) Integer requestsPerSecond,
            @Min(1) Integer requestsPerMinute,
            @Min(1) int burst) {

        public Policy {
            name = name == null || name.isBlank() ? DEFAULT_POLICY_NAME : name;
            methods = methods == null ? List.of() : List.copyOf(methods);
            paths = paths == null ? List.of() : List.copyOf(paths);
        }

        @AssertTrue(message = "give either requests-per-second or requests-per-minute")
        public boolean isOneRateGiven() {
            return (requestsPerSecond == null) != (requestsPerMinute == null);
        }
    }
}
