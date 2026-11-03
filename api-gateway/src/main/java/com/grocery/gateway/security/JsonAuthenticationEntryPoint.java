package com.grocery.gateway.security;

import com.grocery.gateway.exception.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 Unauthorized: the request has no token, or the token is invalid or expired.
 *
 * An EXPIRED access token is normal (it lives 15 minutes): the website then gets a new one with its refresh cookie
 * (POST /api/v1/auth/refresh) and repeats the request. The answer says which case it is, in the message and in the
 * standard WWW-Authenticate header (RFC 6750): Bearer error="invalid_token", error_description="...".
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String message;
        String wwwAuthenticate;
        if (!exchange.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            message = "Please log in to access this resource";
            wwwAuthenticate = "Bearer";
        } else if (isExpired(ex)) {
            message = "Your login has expired. Please refresh the access token or log in again.";
            wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"The access token expired\"";
        } else {
            message = "Your login token is invalid. Please log in again.";
            wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"The access token is invalid\"";
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    /** Spring Security reports an expired JWT as "Jwt expired at 2026-10-28T10:15:30Z" (JwtTimestampValidator). */
    private static boolean isExpired(AuthenticationException ex) {
        return ex instanceof OAuth2AuthenticationException oauth2
                && oauth2.getError().getDescription() != null
                && oauth2.getError().getDescription().contains("expired");
    }
}
