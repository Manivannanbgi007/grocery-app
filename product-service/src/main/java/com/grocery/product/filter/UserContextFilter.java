package com.grocery.product.filter;

import com.grocery.product.constant.HeaderNames;
import com.grocery.product.context.UserContext;
import com.grocery.product.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the user headers that the API gateway adds after validating the JWT,
 * and stores them in {@link UserContextHolder} for the rest of the request.
 *
 * Calling this service directly (without the gateway)? Send the headers yourself,
 * for example X-User-Id: 4 and X-User-Role: CUSTOMER (see http/auth-service.http).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserContext userContext = new UserContext(
                parseId(request.getHeader(HeaderNames.USER_ID)),
                request.getHeader(HeaderNames.USER_ROLE),
                parseId(request.getHeader(HeaderNames.STORE_ID)));

        UserContextHolder.set(userContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();   // threads are reused, so always clean up
        }
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
