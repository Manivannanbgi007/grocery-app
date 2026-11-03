package com.grocery.gateway.filter;

import com.grocery.gateway.config.CorrelationIdContextConfig;
import com.grocery.gateway.constant.HeaderNames;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * First filter for EVERY request (it runs even before security, so rejected requests get an id too).
 *
 * - Keeps the client's X-Correlation-Id, or creates a new one.
 * - Sends it on to the service, which writes it in every log line.
 * - Returns it in the response, so a user can quote it when reporting a problem.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingId = exchange.getRequest().getHeaders().getFirst(HeaderNames.CORRELATION_ID);
        String correlationId = StringUtils.hasText(incomingId) ? incomingId : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HeaderNames.CORRELATION_ID, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HeaderNames.CORRELATION_ID, correlationId);

        // In the Reactor context, so it reaches the MDC of every log line of this request (CorrelationIdContextConfig)
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(Context.of(CorrelationIdContextConfig.CORRELATION_ID, correlationId));
    }
}
