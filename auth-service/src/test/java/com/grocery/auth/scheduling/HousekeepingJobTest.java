package com.grocery.auth.scheduling;

import com.grocery.auth.TestcontainersConfiguration;
import com.grocery.auth.config.JpaAuditingConfig;
import com.grocery.auth.entity.OutboxEvent;
import com.grocery.auth.entity.PasswordResetToken;
import com.grocery.auth.entity.ProcessedEvent;
import com.grocery.auth.entity.User;
import com.grocery.auth.entity.UserSession;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.repository.OutboxEventRepository;
import com.grocery.auth.repository.PasswordResetTokenRepository;
import com.grocery.auth.repository.ProcessedEventRepository;
import com.grocery.auth.repository.SocialLoginFlowRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL: old rows of both event tables are deleted, recent ones are kept (the native DELETE ... LIMIT works).
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})   // auditing fills created_at of the user row
@ActiveProfiles("test")
class HousekeepingJobTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private UserSessionRepository userSessionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private SocialLoginFlowRepository socialLoginFlowRepository;

    @Test
    void deletesOnlyTheRowsOlderThanTheirRetention() {
        outboxEventRepository.saveAndFlush(outboxRow("outbox-15-days-old", NOW.minus(Duration.ofDays(15))));
        outboxEventRepository.saveAndFlush(outboxRow("outbox-13-days-old", NOW.minus(Duration.ofDays(13))));
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-31-days-old", "OrderCreated", NOW.minus(Duration.ofDays(31))));
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-20-days-old", "OrderCreated", NOW.minus(Duration.ofDays(20))));

        newJob().deleteOldEventRows();

        assertThat(outboxEventRepository.existsById("outbox-15-days-old")).isFalse();
        assertThat(outboxEventRepository.existsById("outbox-13-days-old")).isTrue();
        assertThat(processedEventRepository.existsById("processed-31-days-old")).isFalse();
        assertThat(processedEventRepository.existsById("processed-20-days-old")).isTrue();
    }

    private static OutboxEvent outboxRow(String id, Instant createdAt) {
        return new OutboxEvent(id, "order", "18", "OrderCreated", new byte[] {0}, createdAt, null);
    }

    @Test
    void deletesTheLoginSessionsThatEndedMoreThanADayAgo() {
        Long userId = newUser().getId();

        UserSession expiredTwoDaysAgo = session(userId, "a", NOW.minus(Duration.ofDays(9)));          // 7-day token
        UserSession loggedOutTwoDaysAgo = session(userId, "b", NOW.minus(Duration.ofDays(3)));
        loggedOutTwoDaysAgo.revoke(SessionEndReason.LOGOUT, NOW.minus(Duration.ofDays(2)));
        UserSession loggedOutAnHourAgo = session(userId, "c", NOW.minus(Duration.ofDays(3)));
        loggedOutAnHourAgo.revoke(SessionEndReason.LOGOUT, NOW.minus(Duration.ofHours(1)));
        UserSession active = session(userId, "d", NOW);
        userSessionRepository.saveAllAndFlush(List.of(expiredTwoDaysAgo, loggedOutTwoDaysAgo, loggedOutAnHourAgo, active));

        newJob().deleteOldEventRows();

        assertThat(userSessionRepository.findAll()).extracting(UserSession::getTokenHash)
                .contains("c".repeat(64), "d".repeat(64))
                .doesNotContain("a".repeat(64), "b".repeat(64));
    }

    @Test
    void deletesThePasswordResetTokensThatExpiredMoreThanADayAgo() {
        User user = newUser();
        passwordResetTokenRepository.saveAllAndFlush(List.of(
                resetToken(user, "e", NOW.minus(Duration.ofDays(2))),
                resetToken(user, "f", NOW.minus(Duration.ofHours(1))),
                resetToken(user, "g", NOW.plus(Duration.ofMinutes(30)))));

        newJob().deleteOldEventRows();

        assertThat(passwordResetTokenRepository.findAll()).extracting(PasswordResetToken::getTokenHash)
                .contains("f".repeat(64), "g".repeat(64))
                .doesNotContain("e".repeat(64));
    }

    private HousekeepingJob newJob() {
        return new HousekeepingJob(outboxEventRepository, processedEventRepository, userSessionRepository,
                passwordResetTokenRepository, socialLoginFlowRepository,
                Duration.ofDays(14), Duration.ofDays(30), Duration.ofDays(1), Duration.ofDays(1), Duration.ofDays(1));
    }

    private User newUser() {
        User user = new User();
        user.setFullName("Housekeeping Tester");
        user.setEmail("housekeeping." + System.nanoTime() + "@example.com");
        user.setPhone("97" + String.format("%08d", System.nanoTime() % 100_000_000));
        user.setPasswordHash("not-used");
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private static UserSession session(Long userId, String hashLetter, Instant startedAt) {
        return UserSession.start(userId, hashLetter.repeat(64), startedAt, Duration.ofDays(7), Duration.ofDays(30));
    }

    private static PasswordResetToken resetToken(User user, String hashLetter, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hashLetter.repeat(64));
        token.setExpiresAt(expiresAt);
        return token;
    }
}
