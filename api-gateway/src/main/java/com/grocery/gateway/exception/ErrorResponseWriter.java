package com.grocery.gateway.exception;

import com.grocery.gateway.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an {@link ApiResponse} body, so answers created by the gateway itself (401, 403, 404, 429, 503, 504, and the
 * fallback answers) look exactly like the answers of the services.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return writeBody(exchange, status, ApiResponse.error(message, exchange.getRequest().getPath().value()));
    }

    /** A graceful-degradation answer: 200 with substitute data and a message that says why. */
    public Mono<Void> writeFallback(ServerWebExchange exchange, String message, Object data) {
        return writeBody(exchange, HttpStatus.OK, ApiResponse.fallback(message, data, exchange.getRequest().getPath().value()));
    }

    private Mono<Void> writeBody(ServerWebExchange exchange, HttpStatus status, ApiResponse<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();   // part of the response was already sent; nothing more we can do
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
