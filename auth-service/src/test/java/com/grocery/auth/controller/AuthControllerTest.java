package com.grocery.auth.controller;

import com.grocery.auth.constant.HeaderNames;
import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.exception.SessionEndedException;
import com.grocery.auth.exception.UnauthorizedException;
import com.grocery.auth.security.RefreshTokenCookies;
import com.grocery.auth.service.AuthService;
import com.grocery.auth.service.AuthTokens;
import com.grocery.auth.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test: only the controller, filters and GlobalExceptionHandler are started.
 * The service is a mock, so these tests check HTTP status codes, validation and the response shape.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private RefreshTokenCookies refreshTokenCookies;

    @Test
    void register_returns400WithFieldErrors_whenRequestIsInvalid() throws Exception {
        String invalidRequest = """
                { "fullName": "", "email": "not-an-email", "phone": "12345", "password": "short", "role": "CUSTOMER" }
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("fullName", "email", "phone", "password")))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        verifyNoInteractions(authService);
    }

    @Test
    void register_returns400_whenRoleIsNotAKnownValue() throws Exception {
        String request = """
                { "fullName": "Neha", "email": "neha@example.com", "phone": "9876543210",
                  "password": "Password@123", "role": "KING" }
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_returnsTheAccessTokenInTheJson_andTheRefreshTokenOnlyInTheCookie() throws Exception {
        when(authService.login(any(), any())).thenReturn(new AuthTokens(new AuthResponse("jwt-token", "Bearer", 900, sampleUser()),
                new SessionService.IssuedToken("refresh-token-1", Instant.parse("2026-11-04T10:00:00Z"))));
        when(refreshTokenCookies.create("refresh-token-1", Instant.parse("2026-11-04T10:00:00Z")))
                .thenReturn("refresh_token=refresh-token-1; Path=/api/v1/auth; HttpOnly; SameSite=Strict");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "customer1@grocery.com", "password": "Password@123" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.email").value("customer1@grocery.com"))
                .andExpect(header().string("Set-Cookie", "refresh_token=refresh-token-1; Path=/api/v1/auth; HttpOnly; SameSite=Strict"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(not(containsString("refresh-token-1"))));
    }

    @Test
    void refresh_whenTheSessionHasEnded_returns401_andRemovesTheCookie() throws Exception {
        when(authService.refresh(any())).thenThrow(new SessionEndedException());
        when(refreshTokenCookies.clear()).thenReturn("refresh_token=; Path=/api/v1/auth; Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "old")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has ended. Please log in again."))
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=; Path=/api/v1/auth; Max-Age=0")));
    }

    @Test
    void refreshAndLogout_startedByAnotherWebSite_areRefused() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").header("Sec-Fetch-Site", "same-site"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void logout_removesTheCookie_evenWhenTheSessionCannotBeEnded() throws Exception {
        doThrow(new IllegalStateException("database down")).when(authService).logout("token-1");
        when(refreshTokenCookies.clear()).thenReturn("refresh_token=; Path=/api/v1/auth; Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/logout").header("Sec-Fetch-Site", "same-origin").cookie(new Cookie("refresh_token", "token-1")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=; Path=/api/v1/auth; Max-Age=0")));
    }

    @Test
    void login_returns401_whenCredentialsAreWrong() throws Exception {
        when(authService.login(any(), any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "customer1@grocery.com", "password": "WrongPassword1" }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void getCurrentUser_returns401_whenGatewayHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getCurrentUser_usesUserIdFromGatewayHeader_andReturnsCorrelationId() throws Exception {
        when(authService.getCurrentUser(4L)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/v1/auth/me").header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(header().exists(HeaderNames.CORRELATION_ID));
    }

    private UserResponse sampleUser() {
        return new UserResponse(4L, "Priya Sharma", "customer1@grocery.com", "9000000004",
                Role.CUSTOMER, UserStatus.ACTIVE, null, null, Instant.parse("2026-10-25T10:00:00Z"));
    }
}
