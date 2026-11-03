package com.grocery.store.scheduling;

import com.grocery.store.TestcontainersConfiguration;
import com.grocery.store.entity.OutboxEvent;
import com.grocery.store.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL: old outbox rows are deleted, recent ones are kept (the native DELETE ... LIMIT works).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HousekeepingJobTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void deletesOnlyTheRowsOlderThanTheirRetention() {
        outboxEventRepository.saveAndFlush(outboxRow("outbox-15-days-old", NOW.minus(Duration.ofDays(15))));
        outboxEventRepository.saveAndFlush(outboxRow("outbox-13-days-old", NOW.minus(Duration.ofDays(13))));

        new HousekeepingJob(outboxEventRepository, Duration.ofDays(14))
                .deleteOldEventRows();

        assertThat(outboxEventRepository.existsById("outbox-15-days-old")).isFalse();
        assertThat(outboxEventRepository.existsById("outbox-13-days-old")).isTrue();
    }

    private static OutboxEvent outboxRow(String id, Instant createdAt) {
        return new OutboxEvent(id, "order", "18", "OrderCreated", new byte[] {0}, createdAt, null);
    }
}
