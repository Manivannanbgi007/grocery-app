package com.grocery.auth.scheduling;

import com.grocery.auth.repository.OutboxEventRepository;
import com.grocery.auth.repository.PasswordResetTokenRepository;
import com.grocery.auth.repository.ProcessedEventRepository;
import com.grocery.auth.repository.SocialLoginFlowRepository;
import com.grocery.auth.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

/**
 * Every hour (app.housekeeping.cron): deletes the rows of the two event tables nobody needs any more, so they do not
 * grow for ever.
 *
 *   outbox_event     kept 14 days. Debezium publishes a row from the binlog right after it is written; the row itself
 *                    is only read again to republish events after a Kafka Connect outage longer than the 7 days of
 *                    binlog (docs/resilience.md), so it is kept twice as long.
 *   processed_event  kept 30 days. It must outlive every way an old event can arrive again: republished outbox rows
 *                    (up to 14 days old) and replays from a dead-letter topic.
 *
 * It also deletes login sessions, password-reset tokens and social login attempts one day after they ended or expired:
 * nobody can use them.
 *
 * Deletes 1000 rows per transaction, so rows are never locked for long. With several instances of the service, each
 * runs the job; they simply share the work. Debezium publishes nothing for deleted outbox rows.
 */
@Slf4j
@Component
public class HousekeepingJob {

    static final int BATCH_SIZE = 1_000;

    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SocialLoginFlowRepository socialLoginFlowRepository;
    private final Duration outboxRetention;
    private final Duration processedEventRetention;
    private final Duration sessionRetention;
    private final Duration resetTokenRetention;
    private final Duration socialLoginRetention;

    public HousekeepingJob(OutboxEventRepository outboxEventRepository,
                           ProcessedEventRepository processedEventRepository,
                           UserSessionRepository userSessionRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           SocialLoginFlowRepository socialLoginFlowRepository,
                           @Value("${app.housekeeping.outbox-retention}") Duration outboxRetention,
                           @Value("${app.housekeeping.processed-event-retention}") Duration processedEventRetention,
                           @Value("${app.housekeeping.session-retention}") Duration sessionRetention,
                           @Value("${app.housekeeping.reset-token-retention}") Duration resetTokenRetention,
                           @Value("${app.housekeeping.social-login-retention}") Duration socialLoginRetention) {
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRetention = outboxRetention;
        this.processedEventRetention = processedEventRetention;
        this.userSessionRepository = userSessionRepository;
        this.sessionRetention = sessionRetention;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.resetTokenRetention = resetTokenRetention;
        this.socialLoginFlowRepository = socialLoginFlowRepository;
        this.socialLoginRetention = socialLoginRetention;
    }

    @Scheduled(cron = "${app.housekeeping.cron}")
    public void deleteOldEventRows() {
        Instant outboxBefore = Instant.now().minus(outboxRetention);
        Instant processedBefore = Instant.now().minus(processedEventRetention);

        int outboxRows = deleteInBatches(() -> outboxEventRepository.deleteCreatedBefore(outboxBefore, BATCH_SIZE));
        int processedRows = deleteInBatches(() -> processedEventRepository.deleteProcessedBefore(processedBefore, BATCH_SIZE));

        if (outboxRows + processedRows > 0) {
            log.info("Housekeeping: deleted {} outbox row(s) older than {} and {} processed-event row(s) older than {}",
                    outboxRows, outboxRetention, processedRows, processedEventRetention);
        }

        // Login sessions that expired or were ended (logout ...) more than sessionRetention ago: nobody can use them
        Instant sessionsBefore = Instant.now().minus(sessionRetention);
        int sessionRows = deleteInBatches(() -> userSessionRepository.deleteEndedBefore(sessionsBefore, BATCH_SIZE));
        if (sessionRows > 0) {
            log.info("Housekeeping: deleted {} ended login session(s)", sessionRows);
        }

        // Password-reset tokens that expired more than resetTokenRetention ago (used ones expire too)
        Instant resetTokensBefore = Instant.now().minus(resetTokenRetention);
        int resetTokenRows = deleteInBatches(() -> passwordResetTokenRepository.deleteExpiredBefore(resetTokensBefore, BATCH_SIZE));
        if (resetTokenRows > 0) {
            log.info("Housekeeping: deleted {} expired password-reset token(s)", resetTokenRows);
        }

        // Social login attempts (finished or not) whose last step expired more than socialLoginRetention ago
        Instant socialLoginsBefore = Instant.now().minus(socialLoginRetention);
        int socialLoginRows = deleteInBatches(() -> socialLoginFlowRepository.deleteExpiredBefore(socialLoginsBefore, BATCH_SIZE));
        if (socialLoginRows > 0) {
            log.info("Housekeeping: deleted {} old social login attempt(s)", socialLoginRows);
        }
    }

    /** Calls "delete at most BATCH_SIZE rows" until a call deletes fewer; returns the total. */
    private static int deleteInBatches(IntSupplier deleteOneBatch) {
        int total = 0;
        int deleted;
        do {
            deleted = deleteOneBatch.getAsInt();
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
