package com.grocery.auth.controller;

import com.grocery.auth.dto.common.ApiResponse;
import com.grocery.auth.dto.request.SocialLinkRequest;
import com.grocery.auth.dto.request.SocialSessionRequest;
import com.grocery.auth.dto.request.SocialSignupRequest;
import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.dto.response.SocialProviderResponse;
import com.grocery.auth.dto.response.SocialSessionResponse;
import com.grocery.auth.exception.ResourceNotFoundException;
import com.grocery.auth.security.RefreshTokenCookies;
import com.grocery.auth.service.AuthTokens;
import com.grocery.auth.service.SocialLoginService;
import com.grocery.auth.service.SocialLoginService.SocialLoginResult;
import com.grocery.auth.social.SocialClientRegistrations;
import com.grocery.auth.social.SocialFlowCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The steps of social login after the provider's answer (docs/social-login.md). The redirect to the provider and its
 * answer are handled by Spring Security (social/SocialLoginSecurityConfig); these endpoints are called by the shop's
 * page /login/social. They work with the cookie social_flow (the browser that started the attempt), so they refuse
 * requests started by another site, like /refresh.
 */
@Tag(name = "Social login", description = "Log in or sign up with Google, Microsoft, Facebook or Apple")
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialLoginController {

    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    private final SocialLoginService socialLoginService;
    private final SocialClientRegistrations registrations;
    private final RefreshTokenCookies refreshTokenCookies;
    private final SocialFlowCookies socialFlowCookies;

    @Operation(summary = "The providers this shop offers (social login switched on, with credentials); the login pages show a button for each")
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<SocialProviderResponse>>> providers() {
        List<SocialProviderResponse> providers = registrations.all().stream()
                .map(registration -> new SocialProviderResponse(registration.getRegistrationId(), registration.getClientName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Login providers", providers));
    }

    @Operation(summary = "Exchange the one-time code from the provider's login: logged in, or the sign-up or password form is next")
    @PostMapping("/session")
    public ResponseEntity<ApiResponse<SocialSessionResponse>> session(@Valid @RequestBody SocialSessionRequest request,
            @CookieValue(name = SocialFlowCookies.NAME, required = false) String browserToken,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken,
            @RequestHeader(name = SEC_FETCH_SITE, required = false) String fetchSite) {
        requireAvailable();
        TokenResponses.refuseCrossSite(fetchSite);
        SocialLoginResult result = socialLoginService.redeem(request.code(), browserToken, currentRefreshToken);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        AuthResponse auth = null;
        if (result.tokens() != null) {
            auth = result.tokens().response();
            response.header(HttpHeaders.SET_COOKIE,
                    refreshTokenCookies.create(result.tokens().refreshToken().token(), result.tokens().refreshToken().expiresAt()));
            response.header(HttpHeaders.SET_COOKIE, socialFlowCookies.clear());   // the attempt is finished
        }
        SocialSessionResponse body = new SocialSessionResponse(result.status().name(), result.provider(), auth,
                result.fullName(), result.email(), result.emailLocked());
        return response.body(ApiResponse.success(messageOf(result.status()), body));
    }

    @Operation(summary = "Create the account for the provider's login (the mobile number and, if needed, the email)")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signUp(@Valid @RequestBody SocialSignupRequest request,
            @CookieValue(name = SocialFlowCookies.NAME, required = false) String browserToken,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken,
            @RequestHeader(name = SEC_FETCH_SITE, required = false) String fetchSite) {
        requireAvailable();
        TokenResponses.refuseCrossSite(fetchSite);
        AuthTokens tokens = socialLoginService.signUp(browserToken, request, currentRefreshToken);
        return TokenResponses.withTokens(refreshTokenCookies,
                ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE, socialFlowCookies.clear()),
                tokens, "Registration successful");
    }

    @Operation(summary = "Connect the provider's login to the existing account with the same email, with that account's password")
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<AuthResponse>> link(@Valid @RequestBody SocialLinkRequest request,
            @CookieValue(name = SocialFlowCookies.NAME, required = false) String browserToken,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken,
            @RequestHeader(name = SEC_FETCH_SITE, required = false) String fetchSite) {
        requireAvailable();
        TokenResponses.refuseCrossSite(fetchSite);
        AuthTokens tokens = socialLoginService.link(browserToken, request, currentRefreshToken);
        return TokenResponses.withTokens(refreshTokenCookies,
                ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, socialFlowCookies.clear()),
                tokens, "Login successful");
    }

    /** Social login switched off (SOCIAL_LOGIN_ENABLED), or no provider configured: these endpoints are not there. */
    private void requireAvailable() {
        if (!registrations.isAvailable()) {
            throw new ResourceNotFoundException("Social login is not available");
        }
    }

    private static String messageOf(SocialLoginResult.Status status) {
        return switch (status) {
            case LOGGED_IN -> "Login successful";
            case SIGNUP_REQUIRED -> "Please finish creating your account";
            case PASSWORD_REQUIRED -> "An account with this email exists: enter its password to connect";
        };
    }
}
