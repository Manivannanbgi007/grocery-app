package com.grocery.auth;

import com.grocery.auth.entity.OutboxEvent;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.repository.OutboxEventRepository;
import com.grocery.auth.repository.SocialLoginRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.social.TestProviders;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Social login from the button to the session, against a real OpenID provider (mock-oauth2-server in Docker, playing
 * Google, Microsoft, Facebook, Apple and the local test provider) and a real MySQL: Spring Security's redirect, the
 * provider's login form, the callback (state, code, ID token signature, nonce, PKCE; Microsoft's issuer; Facebook's Graph
 * API; Apple's form post), the one-time code, then sign-up, automatic linking or the password to connect, and every
 * refusal.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SocialLoginFlowIntegrationTest {

    private static final String PUBLIC_URL = "http://localhost:5173";
    private static final AtomicInteger RUN = new AtomicInteger();

    /** A real OpenID provider: any user id and any claims, typed into its login form (here: posted by the test). */
    static final GenericContainer<?> PROVIDER = new GenericContainer<>(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:6.1.3"))
            .withExposedPorts(8080)
            .withEnv("JSON_CONFIG", "{\"interactiveLogin\":true}")
            .waitingFor(Wait.forHttp("/google/.well-known/openid-configuration"));

    static {
        PROVIDER.start();
    }

    /**
     * Microsoft's directory of personal accounts. The fake provider plays Microsoft at .../{this id}: its tokens then carry
     * tid = this id and the issuer .../{tid}, so MicrosoftIssuerValidator checks them like Microsoft's
     * (https://login.microsoftonline.com/{tid}/v2.0). The refusals are in MicrosoftIssuerValidatorTest: the fake provider
     * always writes its own tid, so it cannot make a token whose tid and issuer disagree.
     */
    private static final String PERSONAL_ACCOUNTS = "9188040d-6c67-4c5b-b112-36a304b66dad";

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) throws Exception {
        String base = "http://" + PROVIDER.getHost() + ":" + PROVIDER.getMappedPort(8080);
        registry.add("app.social.enabled", () -> "true");
        registry.add("app.social.public-url", () -> PUBLIC_URL);
        // Microsoft, Facebook and Apple, with their addresses pointed at the fake provider
        String microsoft = base + "/" + PERSONAL_ACCOUNTS;
        registry.add("app.social.providers.microsoft.client-id", () -> "grocery-microsoft");
        registry.add("app.social.providers.microsoft.client-secret", () -> "secret");
        registry.add("app.social.providers.microsoft.tenant", () -> "consumers");
        registry.add("app.social.providers.microsoft.issuer-uri", () -> base + "/{tid}");
        registry.add("app.social.providers.microsoft.authorization-uri", () -> microsoft + "/authorize");
        registry.add("app.social.providers.microsoft.token-uri", () -> microsoft + "/token");
        registry.add("app.social.providers.microsoft.jwk-set-uri", () -> microsoft + "/jwks");
        registry.add("app.social.providers.facebook.client-id", () -> "grocery-facebook");
        registry.add("app.social.providers.facebook.client-secret", () -> "secret");
        registry.add("app.social.providers.facebook.authorization-uri", () -> base + "/facebook/authorize");
        registry.add("app.social.providers.facebook.token-uri", () -> base + "/facebook/token");
        registry.add("app.social.providers.facebook.user-info-uri", () -> base + "/facebook/userinfo?fields=id,name,email");
        String appleKey = TestProviders.pem(TestProviders.newAppleKey());
        registry.add("app.social.providers.apple.client-id", () -> "com.example.grocery.web");
        registry.add("app.social.providers.apple.team-id", () -> "TEAM123456");
        registry.add("app.social.providers.apple.key-id", () -> "KEY1234567");
        registry.add("app.social.providers.apple.private-key", () -> appleKey);
        registry.add("app.social.providers.apple.issuer-uri", () -> base + "/apple");
        registry.add("app.social.providers.apple.authorization-uri", () -> base + "/apple/authorize");
        registry.add("app.social.providers.apple.token-uri", () -> base + "/apple/token");
        registry.add("app.social.providers.apple.jwk-set-uri", () -> base + "/apple/jwks");
        // Google, with its addresses pointed at the fake provider (issuer .../google)
        registry.add("app.social.providers.google.client-id", () -> "grocery-google");
        registry.add("app.social.providers.google.client-secret", () -> "secret");
        registry.add("app.social.providers.google.issuer-uri", () -> base + "/google");
        registry.add("app.social.providers.google.authorization-uri", () -> base + "/google/authorize");
        registry.add("app.social.providers.google.token-uri", () -> base + "/google/token");
        registry.add("app.social.providers.google.jwk-set-uri", () -> base + "/google/jwks");
        registry.add("app.social.providers.test.client-id", () -> "grocery-test");
        registry.add("app.social.providers.test.client-secret", () -> "secret");
        registry.add("app.social.providers.test.issuer-uri", () -> base + "/test");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialLoginRepository socialLoginRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    // ----- the flows -----

    @Test
    void theProvidersWithCredentialsAreOffered() throws Exception {
        mockMvc.perform(get("/api/v1/auth/social/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains("google", "microsoft", "facebook", "apple", "test")))
                .andExpect(jsonPath("$.data[*].name").value(contains("Google", "Microsoft", "Facebook", "Apple",
                        "Test provider (local only)")));
    }

    @Test
    void aProviderThatIsNotOffered_hasNoLoginAddress() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/authorization/github"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMicrosoftLogin_passesTheDirectoryCheck_andItsEmailCountsAsVerifiedOnlyWithXmsEdov() throws Exception {
        String email = "ms" + RUN.incrementAndGet() + "@outlook.com";
        redeem(loginAtProvider("microsoft", "ms-" + email, """
                { "email": "%s", "xms_edov": true, "name": "Meera Soft" }
                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.provider").value("microsoft"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailLocked").value(true));

        String unchecked = "ms" + RUN.incrementAndGet() + "@contoso.com";
        redeem(loginAtProvider("microsoft", "ms-" + unchecked, """
                { "email": "%s", "name": "Meera Soft" }
                """.formatted(unchecked)))
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.emailLocked").value(false));
    }

    @Test
    void aFacebookLogin_readsThePersonFromTheGraphApi_andIsNeverLinkedAutomatically() throws Exception {
        User user = existingUser("fb" + RUN.incrementAndGet() + "@example.com", Role.CUSTOMER, UserStatus.ACTIVE, true);
        Attempt attempt = loginAtProvider("facebook", "fb-sub-" + user.getId(), """
                { "id": "fb-%d", "email": "%s", "name": "Farah Book" }
                """.formatted(user.getId(), user.getEmail()));

        // Both emails look verified, but Facebook does not say it checked its own: the password is needed
        redeem(attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PASSWORD_REQUIRED"))
                .andExpect(jsonPath("$.data.provider").value("facebook"));
        postJson("/api/v1/auth/social/link", attempt.browser(), """
                { "password": "Password@123" }
                """)
                .andExpect(status().isOk());
        // Found by Facebook's id (from the Graph API's answer), not by the fake provider's "sub"
        assertThat(socialLoginRepository.findByProviderAndProviderUserId("facebook", "fb-" + user.getId())).isPresent();
    }

    @Test
    void anAppleLogin_answersWithAFormPost_andGivesTheNameOnlyThere() throws Exception {
        String email = "apple" + RUN.incrementAndGet() + "@privaterelay.appleid.com";
        Started started = start("apple");
        assertThat(started.providerUrl()).contains("response_mode=form_post").contains("scope=openid%20name%20email");

        // Apple's page answers with a form that the browser posts to our callback (from Apple's site)
        HttpResponse<String> page = postLoginForm(started.providerUrl(), "apple-" + email, """
                { "email": "%s", "email_verified": "true", "is_private_email": "true" }
                """.formatted(email));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(formField(page.body(), "action", "form")).isEqualTo(PUBLIC_URL + "/api/v1/auth/login/oauth2/code/apple");

        String shopUrl = mockMvc.perform(post("/api/v1/auth/login/oauth2/code/apple")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("code", formField(page.body(), "value", "code"))
                        .param("state", formField(page.body(), "value", "state"))
                        .param("user", "{\"name\":{\"firstName\":\"Asha\",\"lastName\":\"Rao\"},\"email\":\"" + email + "\"}"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(shopUrl).startsWith(PUBLIC_URL + "/login/social#code=");

        redeem(new Attempt(shopUrl.substring(shopUrl.indexOf("#code=") + "#code=".length()), started.browser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailLocked").value(true));
    }

    @Test
    void aNewGoogleUserSignsUpWithAMobileNumber_andNextTimeIsLoggedInAtOnce() throws Exception {
        String email = "new.user" + RUN.incrementAndGet() + "@gmail.com";
        String subject = "google-" + email;
        Attempt attempt = loginAtProvider("google", subject, verifiedClaims(email, "Neha Gupta"));

        redeem(attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.fullName").value("Neha Gupta"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailLocked").value(true))
                .andExpect(jsonPath("$.data.auth").doesNotExist());

        MockHttpServletResponse signedUp = postJson("/api/v1/auth/social/signup", attempt.browser(), """
                { "fullName": "Neha Gupta", "phone": "%s", "role": "CUSTOMER" }
                """.formatted(newPhone()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andReturn().getResponse();
        assertThat(signedUp.getHeaders("Set-Cookie")).anySatisfy(c -> assertThat(c).startsWith("refresh_token=").contains("HttpOnly"))
                .anySatisfy(c -> assertThat(c).startsWith("social_flow=;"));   // the attempt is finished

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();   // Google verified a Gmail address
        assertThat(socialLoginRepository.findByProviderAndProviderUserId("google", subject)).hasValueSatisfying(
                link -> assertThat(link.getUser().getId()).isEqualTo(user.getId()));
        assertThat(eventTypesOf(user)).contains("UserRegistered").doesNotContain("SocialLoginLinked");

        // The same Google account again: logged in at once, the same user
        Attempt again = loginAtProvider("google", subject, verifiedClaims(email, "Neha Gupta"));
        MockHttpServletResponse loggedIn = redeem(again)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.auth.user.id").value(user.getId()))
                .andReturn().getResponse();
        assertThat(loggedIn.getHeader("Cache-Control")).contains("no-store");
        assertThat(loggedIn.getHeaders("Set-Cookie")).anySatisfy(c -> assertThat(c).startsWith("refresh_token="));
    }

    @Test
    void anAccountWhoseEmailIsVerifiedOnBothSides_isLinkedAutomatically_andTheUserIsTold() throws Exception {
        User user = existingUser("linked" + RUN.incrementAndGet() + "@gmail.com", Role.CUSTOMER, UserStatus.ACTIVE, true);

        redeem(loginAtProvider("google", "google-auto-" + user.getId(), verifiedClaims(user.getEmail(), "Priya")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.auth.user.id").value(user.getId()));

        assertThat(socialLoginRepository.existsByUserIdAndProvider(user.getId(), "google")).isTrue();
        assertThat(eventTypesOf(user)).contains("SocialLoginLinked");
    }

    @Test
    void anAccountWhoseEmailWasNeverVerifiedHere_needsItsPasswordOnce() throws Exception {
        // Someone could have registered this email before its owner: a verified Google email alone is not enough
        User user = existingUser("unverified" + RUN.incrementAndGet() + "@gmail.com", Role.CUSTOMER, UserStatus.ACTIVE, false);
        Attempt attempt = loginAtProvider("google", "google-pw-" + user.getId(), verifiedClaims(user.getEmail(), "Rahul"));

        redeem(attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PASSWORD_REQUIRED"))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));

        postJson("/api/v1/auth/social/link", attempt.browser(), """
                { "password": "Wrong@123" }
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        assertThat(socialLoginRepository.existsByUserIdAndProvider(user.getId(), "google")).isFalse();

        postJson("/api/v1/auth/social/link", attempt.browser(), """
                { "password": "Password@123" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(user.getId()));
        assertThat(socialLoginRepository.existsByUserIdAndProvider(user.getId(), "google")).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void adminAndDisabledAccountsAreRefused() throws Exception {
        User admin = existingUser("admin" + RUN.incrementAndGet() + "@gmail.com", Role.ADMIN, UserStatus.ACTIVE, true);
        redeem(loginAtProvider("google", "google-admin-" + admin.getId(), verifiedClaims(admin.getEmail(), "Admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin accounts cannot use social login. Please log in with your password."));

        User disabled = existingUser("disabled" + RUN.incrementAndGet() + "@gmail.com", Role.CUSTOMER, UserStatus.DISABLED, true);
        redeem(loginAtProvider("google", "google-off-" + disabled.getId(), verifiedClaims(disabled.getEmail(), "Off")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is disabled. Please contact support."));
        assertThat(socialLoginRepository.existsByUserIdAndProvider(admin.getId(), "google")).isFalse();
    }

    @Test
    void theOneTimeCodeWorksOnce_andOnlyInTheBrowserThatStarted() throws Exception {
        String email = "once" + RUN.incrementAndGet() + "@gmail.com";
        Attempt attempt = loginAtProvider("test", "test-" + email, verifiedClaims(email, "Once"));

        // Another browser (an attacker who got the code) has no social_flow cookie of this attempt
        mockMvc.perform(post("/api/v1/auth/social/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"code\": \"" + attempt.code() + "\" }")
                        .cookie(new Cookie("social_flow", "someone-elses-browser")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This sign-in has expired or was already used. Please try again."));

        redeem(attempt).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"));
        redeem(attempt).andExpect(status().isBadRequest());   // used
    }

    @Test
    void cancelledAtTheProvider_andAnswersWithAnUnknownState_endOnTheShopPageWithAReason() throws Exception {
        Started started = start("google");
        String state = URLDecoder.decode(UriComponentsBuilder.fromUriString(started.providerUrl()).build().getQueryParams().getFirst("state"),
                StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/v1/auth/login/oauth2/code/google").param("error", "access_denied").param("state", state))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(PUBLIC_URL + "/login/social#error=cancelled"));

        mockMvc.perform(get("/api/v1/auth/login/oauth2/code/google").param("code", "made-up").param("state", "made-up"))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(PUBLIC_URL + "/login/social#error=failed"));
    }

    @Test
    void aProviderEmailThatIsNotVerified_isTypedInAtSignUp() throws Exception {
        Attempt attempt = loginAtProvider("test", "test-unverified-" + RUN.incrementAndGet(),
                "{ \"email\": \"maybe@example.com\", \"email_verified\": false, \"name\": \"Maybe\" }");
        redeem(attempt)
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.emailLocked").value(false));

        String typed = "typed" + RUN.incrementAndGet() + "@example.com";
        postJson("/api/v1/auth/social/signup", attempt.browser(), """
                { "fullName": "Maybe", "email": "%s", "phone": "%s", "role": "VENDOR" }
                """.formatted(typed, newPhone()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.email").value(typed))
                .andExpect(jsonPath("$.data.user.role").value("VENDOR"));
        assertThat(userRepository.findByEmail(typed).orElseThrow().isEmailVerified()).isFalse();
    }

    // ----- helpers -----

    /** A login attempt after the provider's answer: the one-time code and the browser's social_flow cookie. */
    private record Attempt(String code, Cookie browser) {
    }

    private record Started(String providerUrl, Cookie browser) {
    }

    /** The button: Spring Security sends the browser to the provider and ties the attempt to it (cookie social_flow). */
    private Started start(String provider) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/oauth2/authorization/" + provider))
                .andExpect(status().isFound())
                .andReturn().getResponse();
        String cookie = response.getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("social_flow=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth/social");
        String value = cookie.substring("social_flow=".length(), cookie.indexOf(';'));
        String providerUrl = response.getRedirectedUrl();
        assertThat(providerUrl).contains("code_challenge=").contains("code_challenge_method=S256")   // PKCE
                .contains("redirect_uri=" + PUBLIC_URL + "/api/v1/auth/login/oauth2/code/" + provider);   // the public address
        if (!provider.equals("facebook")) {
            assertThat(providerUrl).contains("nonce=");   // OpenID Connect: the ID token must carry it back
        }
        return new Started(providerUrl, new Cookie("social_flow", value));
    }

    /** Button, the provider's login form (as this person), the provider's answer at our callback, the redirect to the shop. */
    private Attempt loginAtProvider(String provider, String subject, String claimsJson) throws Exception {
        Started started = start(provider);
        String shopUrl = callback(started, provider, subject, claimsJson);
        assertThat(shopUrl).startsWith(PUBLIC_URL + "/login/social#code=");
        return new Attempt(shopUrl.substring(shopUrl.indexOf("#code=") + "#code=".length()), started.browser());
    }

    private String callback(Started started, String provider, String subject, String claimsJson) throws Exception {
        String providerUrl = started.providerUrl();
        if (provider.equals("facebook")) {
            // The fake provider only speaks OpenID Connect, so it gets "openid" too (it plays Facebook's login dialog).
            // Our side stays plain OAuth 2.0: it decides by its own request, and asks the Graph API (userinfo) who logged in.
            providerUrl = providerUrl.replace("scope=public_profile", "scope=openid%20public_profile");
        }
        HttpResponse<String> answer = postLoginForm(providerUrl, subject, claimsJson);
        assertThat(answer.statusCode()).isEqualTo(302);
        URI callback = URI.create(answer.headers().firstValue("Location").orElseThrow());

        // As a URI (not a string template): the query is already encoded and must not be encoded twice
        return mockMvc.perform(get(URI.create(callback.getPath() + "?" + callback.getRawQuery())))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
    }

    /** The fake provider's login form: any user id ("sub") and any claims. */
    private HttpResponse<String> postLoginForm(String providerUrl, String subject, String claimsJson) throws Exception {
        String form = "username=" + URLEncoder.encode(subject, StandardCharsets.UTF_8)
                + "&claims=" + URLEncoder.encode(claimsJson, StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(URI.create(providerUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** An attribute of the form post page (form_post): the form's action, or an input's value by its name. */
    private static String formField(String html, String attribute, String name) {
        String pattern = name.equals("form") ? "<form action=\"([^\"]*)\"" : "name=\"" + name + "\" " + attribute + "=\"([^\"]*)\"";
        Matcher matcher = Pattern.compile(pattern).matcher(html);
        assertThat(matcher.find()).as("%s in the form post page", name).isTrue();
        return matcher.group(1);
    }

    private ResultActions redeem(Attempt attempt) throws Exception {
        return postJson("/api/v1/auth/social/session", attempt.browser(), "{ \"code\": \"" + attempt.code() + "\" }");
    }

    private ResultActions postJson(String url, Cookie browser, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body).cookie(browser));
    }

    private static String verifiedClaims(String email, String name) {
        return "{ \"email\": \"%s\", \"email_verified\": true, \"name\": \"%s\" }".formatted(email, name);
    }

    private User existingUser(String email, Role role, UserStatus status, boolean emailVerified) {
        User user = new User();
        user.setFullName("Existing User");
        user.setEmail(email);
        user.setPhone(newPhone());
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        user.setRole(role);
        user.setStatus(status);
        if (emailVerified) {
            user.markEmailVerified(Instant.now());
        }
        return userRepository.saveAndFlush(user);
    }

    private List<String> eventTypesOf(User user) {
        return outboxEventRepository.findByAggregateTypeAndAggregateId("user", String.valueOf(user.getId())).stream()
                .map(OutboxEvent::getEventType).toList();
    }

    private static String newPhone() {
        return "9" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
    }
}
