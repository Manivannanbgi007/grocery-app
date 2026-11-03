package com.grocery.gateway.config;

import com.grocery.gateway.constant.Roles;
import com.grocery.gateway.security.JsonAccessDeniedHandler;
import com.grocery.gateway.security.JsonAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * WHO CAN CALL WHAT. The single place where access rules for the whole platform live.
 *
 * - Every request with "Authorization: Bearer <token>" is checked (signature, expiry, issuer - see JwtConfig).
 * - The rules below are checked top to bottom; the FIRST matching rule wins.
 * - The services behind the gateway trust the X-User-* headers that UserHeadersFilter adds.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * The public account endpoints (POST). An Authorization header sent to them is ignored: login, refresh and logout
     * must work although the browser may still hold an old or expired access token (docs/authentication.md). The three
     * social login steps after the provider's answer are among them (docs/social-login.md).
     */
    static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
            "/api/v1/auth/social/session", "/api/v1/auth/social/signup", "/api/v1/auth/social/link");

    /** Social login: the button's redirect to the provider (GET) and the provider's answer (GET, or Apple's form POST). */
    static final String SOCIAL_LOGIN_START = "/api/v1/auth/oauth2/authorization/*";
    static final String SOCIAL_LOGIN_CALLBACK = "/api/v1/auth/login/oauth2/code/*";

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter) {
        return http
                // A stateless REST API with tokens: no sessions, no cookies, no login form
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                // Browser calls from the React app (rules in CorsConfig)
                .cors(Customizer.withDefaults())
                .authorizeExchange(this::accessRules)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(bearerTokenConverter())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401 as ApiResponse JSON
                        .accessDeniedHandler(accessDeniedHandler))            // 403 as ApiResponse JSON
                .build();
    }

    private void accessRules(ServerHttpSecurity.AuthorizeExchangeSpec exchanges) {
        exchanges
                // ----- Infrastructure -----
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()                 // CORS pre-flight
                .pathMatchers("/actuator/**").permitAll()                            // health, metrics, routes
                .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                        "/v3/api-docs/**", "/*/v3/api-docs").permitAll()             // central Swagger UI

                // ----- Public endpoints (no login needed) -----
                .pathMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()   // register, login,
                                                                                     // refresh, logout, password reset,
                                                                                     // social login steps
                .pathMatchers(HttpMethod.GET, SOCIAL_LOGIN_START, "/api/v1/auth/social/providers").permitAll()
                .pathMatchers(SOCIAL_LOGIN_CALLBACK).permitAll()                     // the provider's answer (GET or POST)
                .pathMatchers(HttpMethod.POST,
                        "/api/v1/payments/webhooks/**").permitAll()                  // Razorpay (checked by its signature)
                .pathMatchers(HttpMethod.GET,                                        // browse the catalog
                        "/api/v1/products/**",
                        "/api/v1/categories/**",
                        "/api/v1/stores/**",
                        "/api/v1/platform/**",
                        "/api/v1/banners/**",
                        "/api/v1/coupons/**",
                        "/media/**").permitAll()                                     // product images
                .pathMatchers("/api/v1/chat/**").permitAll()                         // AI shopping assistant: guests may chat;
                                                                                     // a token, if sent, is still checked

                // ----- Any logged-in user -----
                .pathMatchers("/api/v1/auth/me/**").authenticated()
                .pathMatchers("/api/v1/notifications/**").authenticated()          // every panel has the bell icon
                .pathMatchers(HttpMethod.GET, "/delivery-media/**").authenticated()  // proof-of-delivery photos

                // ----- Panels: the path prefix decides the role -----
                .pathMatchers("/api/v1/super-admin/**").hasRole(Roles.SUPER_ADMIN)
                .pathMatchers("/api/v1/admin/**").hasAnyRole(Roles.ADMIN, Roles.SUPER_ADMIN)
                .pathMatchers("/api/v1/vendor/**").hasRole(Roles.VENDOR)
                .pathMatchers("/api/v1/delivery/**").hasRole(Roles.DELIVERY_PARTNER)
                .pathMatchers("/api/v1/**").hasRole(Roles.CUSTOMER)                 // cart, orders, customers/me ...

                // ----- Everything else (for example /internal/**) is never reachable from outside -----
                .anyExchange().denyAll();
    }

    /**
     * Reads "Authorization: Bearer <token>" like Spring Security does, except on the public account endpoints: there the
     * header is ignored (no token = nothing to check), so an expired access token can never turn a login, refresh or
     * logout into a 401.
     */
    private static ServerAuthenticationConverter bearerTokenConverter() {
        ServerBearerTokenAuthenticationConverter bearerToken = new ServerBearerTokenAuthenticationConverter();
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            if (request.getMethod() == HttpMethod.POST && PUBLIC_AUTH_PATHS.contains(request.getPath().value())) {
                return Mono.empty();
            }
            return bearerToken.convert(exchange);
        };
    }
}
