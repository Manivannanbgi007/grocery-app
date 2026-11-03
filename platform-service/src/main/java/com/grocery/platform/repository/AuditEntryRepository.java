package com.grocery.platform.repository;

import com.grocery.platform.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Super admin search: every filter is optional (null = ignore it). */
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE (:eventType IS NULL OR a.eventType = :eventType)
              AND (:source IS NULL OR a.source = :source)
              AND (:userId IS NULL OR a.userId = :userId)
              AND (:storeId IS NULL OR a.storeId = :storeId)
              AND (:aggregateId IS NULL OR a.aggregateId = :aggregateId)
              AND (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt < :to)
            """)
    Page<AuditEntry> search(String eventType, String source, Long userId, Long storeId, String aggregateId, Instant from, Instant to,
                            Pageable pageable);

    /** Dead-letter replays of one topic: aggregate id "<topic>/<partition>/<offset>" (DeadLetterServiceImpl). */
    List<AuditEntry> findByEventTypeAndAggregateIdStartingWith(String eventType, String aggregateIdPrefix);
}
