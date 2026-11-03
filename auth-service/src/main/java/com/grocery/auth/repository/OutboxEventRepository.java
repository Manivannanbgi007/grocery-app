package com.grocery.auth.repository;

import com.grocery.auth.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);

    /**
     * Deletes at most {@code limit} rows written before {@code before}, in one short transaction; returns how many
     * (HousekeepingJob). Debezium publishes nothing for a deleted outbox row.
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM outbox_event WHERE created_at < :before LIMIT :limit", nativeQuery = true)
    int deleteCreatedBefore(Instant before, int limit);
}
