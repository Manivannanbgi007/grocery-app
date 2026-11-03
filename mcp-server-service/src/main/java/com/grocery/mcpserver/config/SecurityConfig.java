package com.grocery.mcpserver.config;

import com.grocery.mcpserver.constant.HeaderNames;
import com.grocery.mcpserver.dto.common.ApiResponse;
import com.grocery.mcpserver.security.ApiKeyAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Who may call what:
 *
 * | Path                                       | Who                                          |
 * |--------------------------------------------|----------------------------------------------|
 * | /mcp                                       | only with the right X-MCP-API-Key (role MCP_CLIENT) |
 * | /actuator/health/**, /info, /prometheus   | anyone (Kubernetes probes, Prometheus)       |
 * | anything else                              | nobody                                       |
 *
 * The service is never routed by the API gateway, so it cannot be reached from the internet at all;
 * the API key protects it inside the network too.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, McpSecurityProperties security, JsonMapper jsonMapper) throws Exception {
        http
                // No browser, no cookies, no login form: every request carries the API key
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterBefore(new ApiKeyAuthenticationFilter(security.mcpApiKey()), AnonymousAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // The first pass of the request was already checked; async and error dispatches belong to it
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/mcp", "/mcp/**").hasRole(ApiKeyAuthenticationFilter.ROLE)
                        .anyRequest().denyAll())

                // Errors in the same JSON shape as every other grocery service
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, request, HttpStatus.UNAUTHORIZED,
                                "A valid " + HeaderNames.MCP_API_KEY + " header is required", jsonMapper))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, request, HttpStatus.FORBIDDEN,
                                "You do not have permission to access this resource", jsonMapper)));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message,
                                   JsonMapper jsonMapper) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(message, request.getRequestURI()));
    }
}
