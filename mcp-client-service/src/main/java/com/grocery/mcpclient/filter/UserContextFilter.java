package com.grocery.mcpclient.filter;

import com.grocery.mcpclient.constant.HeaderNames;
import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the user headers that the API gateway adds after validating the JWT,
 * and stores them in {@link UserContextHolder} for the rest of the request.
 *
 * The gateway removes these headers from browser requests, so nobody can pretend to be another user.
 * The customer's token (Authorization header, also checked by the gateway) is kept for the customer tools only.
 * Calling this service directly (without the gateway)? Send them yourself, for example X-User-Id: 4.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserContextHolder.set(new UserContext(
                parseId(request.getHeader(HeaderNames.USER_ID)),
                request.getHeader(HeaderNames.USER_ROLE),
                bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION))));
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();   // threads are reused, so always clean up
        }
    }

    /** "Bearer eyJ..." -> "eyJ..." (the gateway has already validated it); anything else -> null. */
    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return null;
        }
        String token = authorization.substring(BEARER.length()).strip();
        return token.isEmpty() ? null : token;
    }

    private Long parseId(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("Ignoring invalid id header value '{}'", headerValue);
            return null;
        }
    }
}
