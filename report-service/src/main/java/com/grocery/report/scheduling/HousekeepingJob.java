package com.grocery.report.scheduling;

import com.grocery.report.repository.ProcessedEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

/**
 * Every hour (app.housekeeping.cron): deletes the processed_event rows nobody needs any more, so the table does not
 * grow for ever. (This service publishes no events, so it has no outbox_event table.)
 *
 *   processed_event  kept 30 days. It must outlive every way an old event can arrive again: events republished from
 *                    an outbox table (up to 14 days old, docs/resilience.md) and replays from a dead-letter topic.
 *
 * Deletes 1000 rows per transaction, so rows are never locked for long. With several instances of the service, each
 * runs the job; they simply share the work.
 */
@Slf4j
@Component
public class HousekeepingJob {

    static final int BATCH_SIZE = 1_000;

    private final ProcessedEventRepository processedEventRepository;
    private final Duration processedEventRetention;

    public HousekeepingJob(ProcessedEventRepository processedEventRepository,
                           @Value("${app.housekeeping.processed-event-retention}") Duration processedEventRetention) {
        this.processedEventRepository = processedEventRepository;
        this.processedEventRetention = processedEventRetention;
    }

    @Scheduled(cron = "${app.housekeeping.cron}")
    public void deleteOldEventRows() {
        Instant processedBefore = Instant.now().minus(processedEventRetention);

        int processedRows = deleteInBatches(() -> processedEventRepository.deleteProcessedBefore(processedBefore, BATCH_SIZE));

        if (processedRows > 0) {
            log.info("Housekeeping: deleted {} processed-event row(s) older than {}", processedRows, processedEventRetention);
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
