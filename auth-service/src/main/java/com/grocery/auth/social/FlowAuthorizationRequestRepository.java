package com.grocery.auth.social;

import com.grocery.auth.entity.SocialLoginFlow;
import com.grocery.auth.repository.SocialLoginFlowRepository;
import com.grocery.auth.security.RefreshTokens;
import com.grocery.auth.security.Sha256;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Where Spring Security keeps a login attempt between "Continue with Google" and the provider's answer: the
 * authorization request, with the random state, the nonce and the PKCE verifier that the answer must match.
 *
 * Spring keeps it in the HTTP session by default; this service has no sessions (any instance may get the answer), so it
 * goes into MySQL (social_login_flows), as JSON: nothing is ever deserialized from bytes. Each state works once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** Tells the success handler which attempt the provider answered (set while the answer is checked). */
    static final String FLOW_ID = FlowAuthorizationRequestRepository.class.getName() + ".flowId";

    private final SocialLoginFlowRepository flows;
    private final SocialFlowCookies cookies;
    private final SocialLoginProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        return flows.findByStateHash(Sha256.hex(state))
                .filter(flow -> flow.getStage() == SocialLoginFlow.Stage.STARTED && flow.getExpiresAt().isAfter(clock.instant()))
                .map(flow -> fromJson(flow.getAuthorizationRequest()))
                .orElse(null);
    }

    /** "Continue with Google" was clicked: keep the request, and tie the attempt to this browser (cookie social_flow). */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;   // Spring's way to say "remove": removeAuthorizationRequest does that
        }
        String browserToken = RefreshTokens.newToken();
        Instant now = clock.instant();
        String provider = authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        flows.save(SocialLoginFlow.start(Sha256.hex(authorizationRequest.getState()), Sha256.hex(browserToken), provider,
                toJson(authorizationRequest), now, now.plus(properties.flowLifetime())));
        // Long enough for the provider's login page and the sign-up form after it
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(browserToken, properties.flowLifetime().plus(properties.signupLifetime())));
    }

    /** The provider came back: the request of this state, once (a second answer with the same state finds nothing). */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        String stateHash = Sha256.hex(state);
        if (flows.markCallback(stateHash, clock.instant()) != 1) {
            log.warn("Social login answer with an unknown, used or expired state");
            return null;   // Spring then fails the login with "authorization_request_not_found"
        }
        SocialLoginFlow flow = flows.findByStateHash(stateHash).orElseThrow();
        request.setAttribute(FLOW_ID, flow.getId());
        return fromJson(flow.getAuthorizationRequest());
    }

    /** The fields of OAuth2AuthorizationRequest, as plain JSON. The attributes hold the nonce and the PKCE verifier: never logged. */
    private record StoredRequest(String authorizationUri, String clientId, String redirectUri, Set<String> scopes, String state,
                                 Map<String, Object> additionalParameters, Map<String, Object> attributes,
                                 String authorizationRequestUri) {
    }

    private String toJson(OAuth2AuthorizationRequest request) {
        return jsonMapper.writeValueAsString(new StoredRequest(request.getAuthorizationUri(), request.getClientId(),
                request.getRedirectUri(), request.getScopes(), request.getState(), request.getAdditionalParameters(),
                request.getAttributes(), request.getAuthorizationRequestUri()));
    }

    private OAuth2AuthorizationRequest fromJson(String json) {
        StoredRequest stored = jsonMapper.readValue(json, StoredRequest.class);
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(stored.authorizationUri())
                .clientId(stored.clientId())
                .redirectUri(stored.redirectUri())
                .scopes(stored.scopes())
                .state(stored.state())
                .additionalParameters(stored.additionalParameters())
                .attributes(stored.attributes())
                .authorizationRequestUri(stored.authorizationRequestUri())
                .build();
    }
}
