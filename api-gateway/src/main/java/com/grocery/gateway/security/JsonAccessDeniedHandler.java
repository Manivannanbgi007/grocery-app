package com.grocery.gateway.security;

import com.grocery.gateway.exception.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 403 Forbidden: the token is valid, but the user's role may not call this path
 * (for example a CUSTOMER calling /api/v1/admin/...).
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource");
    }
}
