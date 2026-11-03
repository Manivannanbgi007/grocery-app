package com.grocery.inventory.scheduling;

import com.grocery.inventory.repository.OutboxEventRepository;
import com.grocery.inventory.repository.ProcessedEventRepository;
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
 * Deletes 1000 rows per transaction, so rows are never locked for long. With several instances of the service, each
 * runs the job; they simply share the work. Debezium publishes nothing for deleted outbox rows.
 */
@Slf4j
@Component
public class HousekeepingJob {

    static final int BATCH_SIZE = 1_000;

    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Duration outboxRetention;
    private final Duration processedEventRetention;

    public HousekeepingJob(OutboxEventRepository outboxEventRepository,
                           ProcessedEventRepository processedEventRepository,
                           @Value("${app.housekeeping.outbox-retention}") Duration outboxRetention,
                           @Value("${app.housekeeping.processed-event-retention}") Duration processedEventRetention) {
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRetention = outboxRetention;
        this.processedEventRetention = processedEventRetention;
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
