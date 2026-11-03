package com.grocery.auth.social;

import com.grocery.auth.service.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The provider's answer is valid (Spring Security checked the state, swapped the code for tokens, and checked the ID
 * token's signature, issuer, audience, expiry and nonce). Here: remember who it is, and send the browser back to the
 * shop with a one-time code. No access token and no cookie are made here: the shop's own page exchanges the code
 * (POST /api/v1/auth/social/session), which works the same for every provider, also for Apple's form post from
 * another site.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialProfiles profiles;
    private final SocialLoginService socialLoginService;
    private final SocialLoginProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        try {
            Long flowId = (Long) request.getAttribute(FlowAuthorizationRequestRepository.FLOW_ID);
            // Apple posts the person's name in the form field "user", at their first login only
            SocialProfile profile = profiles.of(provider, token.getPrincipal(), request.getParameter("user"));
            String code = socialLoginService.acceptProfile(flowId, profile);
            // In the fragment (#): browsers never send it to a server, so it appears in no log and no Referer header
            response.sendRedirect(properties.publicUrl() + "/login/social#code=" + code);
        } catch (RuntimeException ex) {
            log.error("Social login with {} could not be completed", provider, ex);
            response.sendRedirect(properties.publicUrl() + "/login/social#error=failed");
        }
    }
}
