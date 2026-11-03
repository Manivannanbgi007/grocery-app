package com.grocery.auth;

import com.grocery.auth.constant.HeaderNames;
import com.grocery.auth.entity.OutboxEvent;
import com.grocery.auth.repository.OutboxEventRepository;
import com.grocery.auth.repository.PasswordResetTokenRepository;
import com.grocery.auth.security.ResetTokenDecryption;
import com.grocery.auth.security.Sha256;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.UserRegistered;
import com.jayway.jsonpath.JsonPath;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole service (controller -> service -> MySQL + Kafka in Testcontainers).
 * Walks through the full account flow a customer goes through.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.password-reset.event-key}")
    private String eventKey;

    @Test
    void socialLoginIsOffByDefault_noButtons_andItsEndpointsAnswer404() throws Exception {
        // SOCIAL_LOGIN_ENABLED is not set in this test: the login pages get no providers
        mockMvc.perform(get("/api/v1/auth/social/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        // The button's address (404, not a 500 for an unknown provider) and the code exchange
        mockMvc.perform(get("/api/v1/auth/oauth2/authorization/google"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/social/session").contentType(MediaType.APPLICATION_JSON).content("{ \"code\": \"any\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Social login is not available"));
    }

    @Test
    void customerCanRegisterLogInAndResetPassword() throws Exception {
        // 1. Register
        String registerResponse = postJson("/api/v1/auth/register", """
                { "fullName": "Flow Tester", "email": "flow.tester@example.com", "phone": "9811111111",
                  "password": "Password@123", "role": "CUSTOMER" }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        Integer userId = JsonPath.read(registerResponse, "$.data.user.id");

        // 2. A UserRegistered event was saved in the outbox, in the same transaction as the user
        List<OutboxEvent> outbox = outboxEventRepository.findByAggregateTypeAndAggregateId("user", String.valueOf(userId));
        assertThat(outbox).extracting(OutboxEvent::getEventType).containsExactly("UserRegistered");
        UserRegistered event = (UserRegistered) avroDeserializer().deserialize("grocery.user.events", outbox.getFirst().getPayload());
        assertThat(event.getEmail()).isEqualTo("flow.tester@example.com");
        assertThat(event.getEventId()).isEqualTo(outbox.getFirst().getId());

        // 3. The same email cannot register twice
        postJson("/api/v1/auth/register", """
                { "fullName": "Copy Cat", "email": "flow.tester@example.com", "phone": "9822222222",
                  "password": "Password@123", "role": "CUSTOMER" }
                """)
                .andExpect(status().isConflict());

        // 4. Read my account (the gateway would add X-User-Id from the JWT)
        mockMvc.perform(get("/api/v1/auth/me").header(HeaderNames.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("flow.tester@example.com"));

        // 5. Forgot password -> token (exposed in the test profile)
        String firstToken = forgotPassword("flow.tester@example.com");

        // The database keeps only the token's hash; the event carries the token encrypted, never in plain text
        assertThat(passwordResetTokenRepository.findByTokenHash(Sha256.hex(firstToken))).isPresent();
        OutboxEvent resetEvent = outboxEventRepository.findByAggregateTypeAndAggregateId("user", String.valueOf(userId)).stream()
                .filter(row -> row.getEventType().equals("PasswordResetRequested")).findFirst().orElseThrow();
        assertThat(new String(resetEvent.getPayload(), StandardCharsets.ISO_8859_1)).doesNotContain(firstToken);
        PasswordResetRequested requested = (PasswordResetRequested) avroDeserializer().deserialize("grocery.user.events", resetEvent.getPayload());
        assertThat(ResetTokenDecryption.decrypt(requested.getEncryptedResetToken(), userId, eventKey)).isEqualTo(firstToken);

        // 5a. A newer email's token replaces it: the first token no longer works
        String resetToken = forgotPassword("flow.tester@example.com");
        postJson("/api/v1/auth/reset-password", """
                { "token": "%s", "newPassword": "NewPassword@456" }
                """.formatted(firstToken))
                .andExpect(status().isBadRequest());

        // 6. Reset the password
        postJson("/api/v1/auth/reset-password", """
                { "token": "%s", "newPassword": "NewPassword@456" }
                """.formatted(resetToken))
                .andExpect(status().isOk());

        // 7. The old password no longer works, the new one does
        postJson("/api/v1/auth/login", """
                { "email": "flow.tester@example.com", "password": "Password@123" }
                """)
                .andExpect(status().isUnauthorized());

        postJson("/api/v1/auth/login", """
                { "email": "flow.tester@example.com", "password": "NewPassword@456" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.lastLoginAt").isNotEmpty());

        // 8. A reset token works only once
        postJson("/api/v1/auth/reset-password", """
                { "token": "%s", "newPassword": "Another@789" }
                """.formatted(resetToken))
                .andExpect(status().isBadRequest());
    }

    /** POST /forgot-password; the token from the answer (exposed in the test profile only). */
    private String forgotPassword(String email) throws Exception {
        String response = postJson("/api/v1/auth/forgot-password", """
                { "email": "%s" }
                """.formatted(email))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.resetToken");
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private KafkaAvroDeserializer avroDeserializer() {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(Map.of("schema.registry.url", "mock://grocery-tests", "specific.avro.reader", true), false);
        return deserializer;
    }
}
