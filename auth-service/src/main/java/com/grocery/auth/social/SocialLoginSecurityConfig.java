package com.grocery.auth.social;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security in auth-service, for social login only.
 *
 * The API gateway checks every access token; auth-service had no security filter of its own, and keeps it that way for
 * every existing endpoint (the second chain lets everything through, with nothing switched on). Only the two social
 * login addresses go through Spring Security's OAuth 2.0 / OpenID Connect login (the first chain):
 *
 *   GET      /api/v1/auth/oauth2/authorization/{provider}   redirect to the provider (with state, nonce, PKCE)
 *   GET/POST /api/v1/auth/login/oauth2/code/{provider}      the provider's answer (Apple posts a form)
 *
 * No HTTP session (any instance may get the answer: the attempt is in MySQL), no CSRF token on these two (the random
 * state does that job, and Apple's form post could not carry one).
 */
@Configuration
public class SocialLoginSecurityConfig {

    public static final String AUTHORIZATION_PATH = "/api/v1/auth/oauth2/authorization";

    @Bean
    @Order(1)
    SecurityFilterChain socialLoginChain(HttpSecurity http, SocialClientRegistrations registrations,
                                         FlowAuthorizationRequestRepository authorizationRequests,
                                         SocialLoginSuccessHandler successHandler,
                                         SocialLoginFailureHandler failureHandler) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, AUTHORIZATION_PATH);
        resolver.setAuthorizationRequestCustomizer(request -> request.attributes(attributes -> {
            if ("apple".equals(attributes.get(OAuth2ParameterNames.REGISTRATION_ID))) {
                // Apple sends its answer as a form POST (it must, when the name and email are asked for)
                request.additionalParameters(parameters -> parameters.put("response_mode", "form_post"));
            }
        }));
        // Facebook only: who logged in, from the Graph API, with the appsecret_proof
        DefaultOAuth2UserService userService = new DefaultOAuth2UserService();
        userService.setRequestEntityConverter(new FacebookAppSecretProof());
        return http
                .securityMatcher(AUTHORIZATION_PATH + "/*", SocialClientRegistrations.CALLBACK_PATH + "*")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(login -> login
                        .clientRegistrationRepository(registrations)
                        .authorizedClientRepository(new NothingStored())
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new OfferedProvidersOnly(resolver, registrations))
                                .authorizationRequestRepository(authorizationRequests))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(SocialClientRegistrations.CALLBACK_PATH + "*"))
                        .userInfoEndpoint(endpoint -> endpoint.userService(userService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .build();
    }

    /**
     * How ID tokens are checked (oauth2Login finds this bean by its type): Spring's checks (signature, issuer, audience,
     * expiry, nonce) for every provider, plus Microsoft's issuer, which has the person's own directory in it.
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(SocialLoginProperties properties) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        // The same as Spring's default (createDefaultWithValidators also checks the times), plus Microsoft's issuer
        factory.setJwtValidatorFactory(registration -> "microsoft".equals(registration.getRegistrationId())
                ? JwtValidators.createDefaultWithValidators(new OidcIdTokenValidator(registration),
                        new MicrosoftIssuerValidator(properties.providers().get("microsoft")))
                : JwtValidators.createDefaultWithValidators(new OidcIdTokenValidator(registration)));
        return factory;
    }

    /** Every other endpoint: exactly as before Spring Security was added (the gateway decides who may call what). */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain everythingElseChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * "Continue with X" for a provider that is not offered (social login switched off, the provider switched off, or no
     * credentials): no redirect. The request goes on and ends in a 404, instead of Spring's 500 for an unknown provider.
     */
    private record OfferedProvidersOnly(OAuth2AuthorizationRequestResolver resolver, SocialClientRegistrations registrations)
            implements OAuth2AuthorizationRequestResolver {

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String start = AUTHORIZATION_PATH + "/";
            if (path.startsWith(start) && registrations.findByRegistrationId(path.substring(start.length())) == null) {
                return null;
            }
            return resolver.resolve(request);
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            return registrations.findByRegistrationId(clientRegistrationId) == null ? null : resolver.resolve(request, clientRegistrationId);
        }
    }

    /**
     * The provider's access token is used once, to get who logged in, and then forgotten: we never call the provider's
     * APIs on the user's behalf (Spring would otherwise keep every token in memory).
     */
    private static final class NothingStored implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
            // not stored
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {
            // nothing to remove
        }
    }
}
