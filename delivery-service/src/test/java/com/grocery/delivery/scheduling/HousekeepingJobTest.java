package com.grocery.delivery.scheduling;

import com.grocery.delivery.TestcontainersConfiguration;
import com.grocery.delivery.entity.OutboxEvent;
import com.grocery.delivery.entity.ProcessedEvent;
import com.grocery.delivery.repository.OutboxEventRepository;
import com.grocery.delivery.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL: old rows of both event tables are deleted, recent ones are kept (the native DELETE ... LIMIT works).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HousekeepingJobTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void deletesOnlyTheRowsOlderThanTheirRetention() {
        outboxEventRepository.saveAndFlush(outboxRow("outbox-15-days-old", NOW.minus(Duration.ofDays(15))));
        outboxEventRepository.saveAndFlush(outboxRow("outbox-13-days-old", NOW.minus(Duration.ofDays(13))));
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-31-days-old", "OrderCreated", NOW.minus(Duration.ofDays(31))));
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-20-days-old", "OrderCreated", NOW.minus(Duration.ofDays(20))));

        new HousekeepingJob(outboxEventRepository, processedEventRepository, Duration.ofDays(14), Duration.ofDays(30))
                .deleteOldEventRows();

        assertThat(outboxEventRepository.existsById("outbox-15-days-old")).isFalse();
        assertThat(outboxEventRepository.existsById("outbox-13-days-old")).isTrue();
        assertThat(processedEventRepository.existsById("processed-31-days-old")).isFalse();
        assertThat(processedEventRepository.existsById("processed-20-days-old")).isTrue();
    }

    private static OutboxEvent outboxRow(String id, Instant createdAt) {
        return new OutboxEvent(id, "order", "18", "OrderCreated", new byte[] {0}, createdAt, null);
    }
}
