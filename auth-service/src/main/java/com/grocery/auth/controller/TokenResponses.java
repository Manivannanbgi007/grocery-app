package com.grocery.auth.controller;

import com.grocery.auth.dto.common.ApiResponse;
import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.exception.ForbiddenException;
import com.grocery.auth.security.RefreshTokenCookies;
import com.grocery.auth.service.AuthTokens;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** What the login endpoints share: the answer with the tokens, and the check that a request comes from our own pages. */
final class TokenResponses {

    private TokenResponses() {
    }

    /** The JSON answer with the access token; the refresh token goes into the cookie (when there is a new one). */
    static ResponseEntity<ApiResponse<AuthResponse>> withTokens(RefreshTokenCookies refreshTokenCookies,
            ResponseEntity.BodyBuilder response, AuthTokens tokens, String message) {
        response.cacheControl(CacheControl.noStore());   // an answer with tokens must never be stored by a browser or proxy
        if (tokens.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE,
                    refreshTokenCookies.create(tokens.refreshToken().token(), tokens.refreshToken().expiresAt()));
        }
        return response.body(ApiResponse.success(message, tokens.response()));
    }

    /**
     * Endpoints that work with a cookie alone (/refresh, /logout, the social login steps) must not be triggered by
     * another web site. SameSite=Strict already keeps the cookie away from such requests; browsers also say where a
     * request comes from (Sec-Fetch-Site): only "same-origin" (our own pages) and "none" (typed in, a tool) are accepted.
     * Tools such as curl send no Sec-Fetch-Site at all.
     */
    static void refuseCrossSite(String fetchSite) {
        if (fetchSite != null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) {
            throw new ForbiddenException("This request must come from the shop's own pages");
        }
    }
}
