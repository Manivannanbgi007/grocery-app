package com.grocery.mcpserver.security;

import com.grocery.mcpserver.constant.HeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lets a request in when it carries the right API key in the X-MCP-API-Key header (only mcp-client-service knows it).
 *
 * - Right key: the request is authenticated as "mcp-client-service" with the role MCP_CLIENT.
 * - Missing or wrong key: nothing happens here; SecurityConfig then answers 401.
 *
 * The keys are compared with MessageDigest.isEqual, which takes the same time whatever the key looks like,
 * so nobody can find the key by measuring how fast wrong keys are rejected.
 * Created in SecurityConfig (not a @Component, which would also register it as a normal servlet filter).
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE = "MCP_CLIENT";
    private static final String CLIENT_NAME = "mcp-client-service";

    private final byte[] expectedKey;

    public ApiKeyAuthenticationFilter(String apiKey) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sentKey = request.getHeader(HeaderNames.MCP_API_KEY);
        if (sentKey != null) {
            if (MessageDigest.isEqual(sentKey.getBytes(StandardCharsets.UTF_8), expectedKey)) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        CLIENT_NAME, null, AuthorityUtils.createAuthorityList("ROLE_" + ROLE)));
                SecurityContextHolder.setContext(context);
            } else {
                // Never log the key itself
                log.warn("Rejected a request to {} with a wrong {} header (from {})",
                        request.getRequestURI(), HeaderNames.MCP_API_KEY, request.getRemoteAddr());
            }
        }
        filterChain.doFilter(request, response);
    }
}
