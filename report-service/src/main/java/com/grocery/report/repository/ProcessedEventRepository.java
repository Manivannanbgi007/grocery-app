package com.grocery.report.repository;

import com.grocery.report.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /** Deletes at most {@code limit} rows handled before {@code before}, in one short transaction (HousekeepingJob). */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM processed_event WHERE processed_at < :before LIMIT :limit", nativeQuery = true)
    int deleteProcessedBefore(Instant before, int limit);
}
