package com.grocery.store.scheduling;

import com.grocery.store.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

/**
 * Every hour (app.housekeeping.cron): deletes the outbox rows nobody needs any more, so the table does not grow for
 * ever. (This service handles no Kafka events, so it has no processed_event table.)
 *
 *   outbox_event     kept 14 days. Debezium publishes a row from the binlog right after it is written; the row itself
 *                    is only read again to republish events after a Kafka Connect outage longer than the 7 days of
 *                    binlog (docs/resilience.md), so it is kept twice as long.
 *
 * Deletes 1000 rows per transaction, so rows are never locked for long. With several instances of the service, each
 * runs the job; they simply share the work. Debezium publishes nothing for deleted outbox rows.
 */
@Slf4j
@Component
public class HousekeepingJob {

    static final int BATCH_SIZE = 1_000;

    private final OutboxEventRepository outboxEventRepository;
    private final Duration outboxRetention;

    public HousekeepingJob(OutboxEventRepository outboxEventRepository,
                           @Value("${app.housekeeping.outbox-retention}") Duration outboxRetention) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxRetention = outboxRetention;
    }

    @Scheduled(cron = "${app.housekeeping.cron}")
    public void deleteOldEventRows() {
        Instant outboxBefore = Instant.now().minus(outboxRetention);

        int outboxRows = deleteInBatches(() -> outboxEventRepository.deleteCreatedBefore(outboxBefore, BATCH_SIZE));

        if (outboxRows > 0) {
            log.info("Housekeeping: deleted {} outbox row(s) older than {}", outboxRows, outboxRetention);
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
