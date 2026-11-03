package com.grocery.gateway.filter;

import com.grocery.gateway.constant.HeaderNames;
import com.grocery.gateway.constant.JwtClaims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs for every request that is routed to a service, AFTER Spring Security has validated the JWT.
 *
 * 1. Removes any X-User-* headers the client sent - nobody can pretend to be another user.
 * 2. For logged-in users, adds X-User-Id, X-User-Role and X-Store-Id taken from the JWT,
 *    so services never have to read tokens themselves.
 */
@Component
public class UserHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> ((JwtAuthenticationToken) principal).getToken())
                .map(jwt -> withUserHeaders(exchange, jwt))
                // Anonymous request (public endpoint): only remove the headers
                .switchIfEmpty(Mono.fromSupplier(() -> withUserHeaders(exchange, null)))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HeaderNames.USER_ID);
                    headers.remove(HeaderNames.USER_ROLE);
                    headers.remove(HeaderNames.STORE_ID);

                    if (jwt != null) {
                        headers.set(HeaderNames.USER_ID, jwt.getSubject());
                        headers.set(HeaderNames.USER_ROLE, jwt.getClaimAsString(JwtClaims.ROLE));
                        Object storeId = jwt.getClaim(JwtClaims.STORE_ID);
                        if (storeId != null) {
                            headers.set(HeaderNames.STORE_ID, String.valueOf(storeId));
                        }
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;   // before the routing filters forward the request
    }
}
