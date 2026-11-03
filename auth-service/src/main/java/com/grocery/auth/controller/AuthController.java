package com.grocery.auth.controller;

import com.grocery.auth.context.UserContextHolder;
import com.grocery.auth.dto.common.ApiResponse;
import com.grocery.auth.dto.request.ChangePasswordRequest;
import com.grocery.auth.dto.request.ForgotPasswordRequest;
import com.grocery.auth.dto.request.LoginRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.request.ResetPasswordRequest;
import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.dto.response.ForgotPasswordResponse;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.security.RefreshTokenCookies;
import com.grocery.auth.service.AuthService;
import com.grocery.auth.service.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints for every user.
 * register, login, refresh, logout, forgot-password and reset-password are public;
 * /me endpoints need a logged-in user (X-User-Id header from the gateway).
 *
 * Login and register answer with a short-lived access token in the JSON and a refresh token in an HttpOnly cookie
 * (RefreshTokenCookies); /refresh exchanges that cookie for a new access token and a new cookie. docs/authentication.md
 */
@Slf4j
@Tag(name = "Authentication", description = "Register, log in and manage your own account")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    @Operation(summary = "Register as a customer, vendor or delivery partner")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken) {
        AuthTokens tokens = authService.register(request, currentRefreshToken);
        return TokenResponses.withTokens(refreshTokenCookies, ResponseEntity.status(HttpStatus.CREATED), tokens, "Registration successful");
    }

    @Operation(summary = "Log in with email and password: an access token, and a refresh token in an HttpOnly cookie")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken) {
        return TokenResponses.withTokens(refreshTokenCookies, ResponseEntity.ok(), authService.login(request, currentRefreshToken), "Login successful");
    }

    @Operation(summary = "A new access token for the refresh cookie (the cookie is replaced too)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = SEC_FETCH_SITE, required = false) String fetchSite) {
        TokenResponses.refuseCrossSite(fetchSite);
        return TokenResponses.withTokens(refreshTokenCookies, ResponseEntity.ok(), authService.refresh(refreshToken), "Token refreshed");
    }

    @Operation(summary = "Log out: ends the session of the refresh cookie and removes the cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = SEC_FETCH_SITE, required = false) String fetchSite) {
        TokenResponses.refuseCrossSite(fetchSite);
        try {
            authService.logout(refreshToken);
        } catch (RuntimeException ex) {
            // The cookie is removed anyway: this browser is logged out even if the database could not be reached
            log.warn("Logout could not end the session in the database: {}", ex.toString());
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear()).build();
    }

    @Operation(summary = "Get the logged-in user's account")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        Long userId = UserContextHolder.getRequiredUserId();
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", authService.getCurrentUser(userId)));
    }

    @Operation(summary = "Change the logged-in user's password (every other session of the user ends)")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String currentRefreshToken) {
        Long userId = UserContextHolder.getRequiredUserId();
        authService.changePassword(userId, request, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @Operation(summary = "Request a password reset token")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If the email is registered, password reset instructions have been sent", response));
    }

    @Operation(summary = "Set a new password using a reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully"));
    }
}
