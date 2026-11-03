package com.grocery.report.scheduling;

import com.grocery.report.TestcontainersConfiguration;
import com.grocery.report.entity.ProcessedEvent;
import com.grocery.report.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL: old processed_event rows are deleted, recent ones are kept (the native DELETE ... LIMIT works).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HousekeepingJobTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void deletesOnlyTheRowsOlderThanTheirRetention() {
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-31-days-old", "OrderCreated", NOW.minus(Duration.ofDays(31))));
        processedEventRepository.saveAndFlush(new ProcessedEvent("processed-20-days-old", "OrderCreated", NOW.minus(Duration.ofDays(20))));

        new HousekeepingJob(processedEventRepository, Duration.ofDays(30))
                .deleteOldEventRows();

        assertThat(processedEventRepository.existsById("processed-31-days-old")).isFalse();
        assertThat(processedEventRepository.existsById("processed-20-days-old")).isTrue();
    }
}
