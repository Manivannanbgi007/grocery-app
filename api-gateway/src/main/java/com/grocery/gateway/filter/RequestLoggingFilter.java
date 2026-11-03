package com.grocery.gateway.filter;

import com.grocery.gateway.constant.HeaderNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Writes one log line per request:  GET /api/v1/auth/me -> 200 OK (12 ms) correlationId=...
 * Actuator calls (health checks, metrics scraping) are skipped to keep the log readable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        return chain.filter(exchange)
                .doFinally(signal -> log.info("{} {} -> {} ({} ms) correlationId={}",
                        request.getMethod(),
                        request.getPath(),
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - startTime,
                        request.getHeaders().getFirst(HeaderNames.CORRELATION_ID)));
    }
}
