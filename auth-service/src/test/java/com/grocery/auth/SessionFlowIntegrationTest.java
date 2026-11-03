package com.grocery.auth;

import com.grocery.auth.entity.UserSession;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.repository.UserSessionRepository;
import com.grocery.auth.security.RefreshTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Login sessions with a real MySQL: the refresh cookie, rotation, the 10-second grace, reuse detection, logout,
 * expiry after 7 and 30 days, and every way a session is ended. The Clock is a spy, so a test can jump ahead in time.
 * Kafka is not needed (the outbox only writes rows), so its listeners stay off.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SessionFlowIntegrationTest {

    private static final AtomicInteger USERS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserSessionRepository sessionRepository;
    @MockitoSpyBean
    private Clock clock;

    /** How far the test has moved the clock ahead */
    private final AtomicReference<Duration> timeTravel = new AtomicReference<>(Duration.ZERO);

    @BeforeEach
    void controllableClock() {
        doAnswer(call -> Instant.now().plus(timeTravel.get())).when(clock).instant();
    }

    @Test
    void registerAndLogin_setTheRefreshCookie_withTheSafeAttributes_andNeverPutItInTheJson() throws Exception {
        MockHttpServletResponse response = register();

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refresh_token=")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict")
                .containsPattern("Max-Age=60479[0-9];|Max-Age=604800;");   // 7 days (a few ms may have passed)
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain(tokenIn(response))
                .contains("\"expiresInSeconds\":900");   // the access token lives 15 minutes
        UserSession session = sessionOf(tokenIn(response));
        assertThat(session.getSessionExpiresAt()).isAfter(session.getExpiresAt().plus(Duration.ofDays(22)));   // 30 days
    }

    @Test
    void refresh_replacesTheToken_andAnOldTokenWithinTheGraceGetsAnAccessTokenOnly() throws Exception {
        String first = tokenIn(register());

        MockHttpServletResponse refreshed = refresh(first);
        String second = tokenIn(refreshed);
        assertThat(refreshed.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(refreshed.getContentAsString(), "$.data.accessToken")).isNotBlank();
        assertThat(second).isNotEqualTo(first);
        assertThat(sessionOf(second).getPreviousTokenHash()).isEqualTo(RefreshTokens.hash(first));

        // A second tab sends the old token a moment later: an access token, and the cookie stays as it is
        MockHttpServletResponse secondTab = refresh(first);
        assertThat(secondTab.getStatus()).isEqualTo(200);
        assertThat(secondTab.getHeader("Set-Cookie")).isNull();
        assertThat(refresh(second).getStatus()).isEqualTo(200);   // the new token still works
    }

    @Test
    void anOldTokenAfterTheGrace_isReuse_andEndsTheWholeSession(CapturedOutput output) throws Exception {
        String stolen = tokenIn(register());
        String current = tokenIn(refresh(stolen));
        timeTravel.set(Duration.ofSeconds(11));

        MockHttpServletResponse reuse = refresh(stolen);

        assertThat(reuse.getStatus()).isEqualTo(401);
        assertThat(reuse.getHeader("Set-Cookie")).startsWith("refresh_token=;").contains("Max-Age=0");
        assertThat(refresh(current).getStatus()).isEqualTo(401);   // the thief AND the user are logged out
        assertThat(sessionRepository.findByTokenHash(RefreshTokens.hash(current)).orElseThrow().getRevokedReason())
                .isEqualTo(SessionEndReason.REUSE_DETECTED);
        assertThat(output.getOut()).contains("was used again after it had been replaced")
                .doesNotContain(stolen).doesNotContain(current);   // tokens never appear in the log
    }

    @Test
    void twoRefreshesWithTheSameCookieAtTheSameMoment_rotateOnce_andBothGetAnAccessToken() throws Exception {
        String token = tokenIn(register());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> calls = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return refresh(token);
                }));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> call : calls) {
                responses.add(call.get());
            }
            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(200);
            assertThat(responses).filteredOn(response -> response.getHeader("Set-Cookie") != null).hasSize(1);   // one rotation
        }
    }

    @Test
    void logout_endsTheSession_andRemovesTheCookie() throws Exception {
        String token = tokenIn(register());

        MockHttpServletResponse logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie(token)))
                .andReturn().getResponse();

        assertThat(logout.getStatus()).isEqualTo(204);
        assertThat(logout.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(refresh(token).getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/auth/logout")).andReturn().getResponse().getStatus()).isEqualTo(204);   // no cookie: fine
    }

    @Test
    void aTokenExpiresAfter7DaysUnused_andEverySessionAfter30Days() throws Exception {
        String unused = tokenIn(register());
        timeTravel.set(Duration.ofDays(7).plusSeconds(1));
        assertThat(refresh(unused).getStatus()).isEqualTo(401);

        timeTravel.set(Duration.ZERO);
        String token = tokenIn(register());
        for (int day = 6; day <= 24; day += 6) {   // refreshed every 6 days: stays logged in ...
            timeTravel.set(Duration.ofDays(day));
            MockHttpServletResponse response = refresh(token);
            assertThat(response.getStatus()).as("refresh on day %d", day).isEqualTo(200);
            token = tokenIn(response);
        }
        timeTravel.set(Duration.ofDays(30));   // ... but not longer than 30 days
        assertThat(refresh(token).getStatus()).isEqualTo(401);
    }

    @Test
    void disablingTheAccount_orChangingTheRole_endsEverySession() throws Exception {
        MockHttpServletResponse first = register();
        MockHttpServletResponse second = register();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userIdIn(first)).header("X-User-Id", "2").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{ \"status\": \"DISABLED\" }"));
        mockMvc.perform(patch("/api/v1/super-admin/users/{id}/role", userIdIn(second)).header("X-User-Id", "1").header("X-User-Role", "SUPER_ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{ \"role\": \"VENDOR\" }"));

        assertThat(refresh(tokenIn(first)).getStatus()).isEqualTo(401);
        assertThat(refresh(tokenIn(second)).getStatus()).isEqualTo(401);
        assertThat(sessionOf(tokenIn(first)).getRevokedReason()).isEqualTo(SessionEndReason.USER_DISABLED);
        assertThat(sessionOf(tokenIn(second)).getRevokedReason()).isEqualTo(SessionEndReason.ROLE_CHANGED);
    }

    @Test
    void changingThePassword_keepsThisSession_andEndsTheOthers() throws Exception {
        MockHttpServletResponse registered = register();
        String email = JsonPath.read(registered.getContentAsString(), "$.data.user.email");
        String otherDevice = tokenIn(login(email, null));

        mockMvc.perform(put("/api/v1/auth/me/password").cookie(cookie(tokenIn(registered)))
                        .header("X-User-Id", String.valueOf(userIdIn(registered))).header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"currentPassword\": \"Password@123\", \"newPassword\": \"NewPassword@456\" }"))
                .andReturn();

        assertThat(refresh(tokenIn(registered)).getStatus()).isEqualTo(200);
        assertThat(refresh(otherDevice).getStatus()).isEqualTo(401);
        assertThat(sessionOf(otherDevice).getRevokedReason()).isEqualTo(SessionEndReason.PASSWORD_CHANGED);
    }

    @Test
    void loggingInAgainInTheSameBrowser_replacesItsOldSession() throws Exception {
        MockHttpServletResponse registered = register();
        String email = JsonPath.read(registered.getContentAsString(), "$.data.user.email");

        String newToken = tokenIn(login(email, tokenIn(registered)));

        assertThat(refresh(tokenIn(registered)).getStatus()).isEqualTo(401);
        assertThat(sessionOf(tokenIn(registered)).getRevokedReason()).isEqualTo(SessionEndReason.SUPERSEDED);
        assertThat(refresh(newToken).getStatus()).isEqualTo(200);
    }

    @Test
    void noOrAnUnknownCookie_is401() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/auth/refresh")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh("not-a-real-token").getStatus()).isEqualTo(401);
    }

    // ----- helpers -----

    private MockHttpServletResponse register() throws Exception {
        int n = USERS.incrementAndGet();
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Session Tester %d", "email": "session.%d.%d@example.com", "phone": "98%08d",
                          "password": "Password@123", "role": "CUSTOMER" }
                        """.formatted(n, n, System.nanoTime() % 100000, (System.nanoTime() + n) % 100_000_000)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse login(String email, String currentToken) throws Exception {
        var request = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"password\": \"Password@123\" }".formatted(email));
        if (currentToken != null) {
            request.cookie(cookie(currentToken));
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie(token)).header("Sec-Fetch-Site", "same-origin"))
                .andReturn().getResponse();
    }

    private static Cookie cookie(String token) {
        return new Cookie("refresh_token", token);
    }

    /** The refresh token from "Set-Cookie: refresh_token=<token>; Path=..." */
    private static String tokenIn(MockHttpServletResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).as("Set-Cookie").startsWith("refresh_token=");
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    private static int userIdIn(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.data.user.id");
    }

    private UserSession sessionOf(String token) {
        String hash = RefreshTokens.hash(token);
        return sessionRepository.findByTokenHash(hash)
                .or(() -> sessionRepository.findAll().stream().filter(s -> hash.equals(s.getPreviousTokenHash())).findFirst())
                .orElseThrow();
    }
}
