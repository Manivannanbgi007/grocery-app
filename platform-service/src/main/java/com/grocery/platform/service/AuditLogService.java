package com.grocery.platform.service;

import com.grocery.platform.dto.common.PageResponse;
import com.grocery.platform.dto.response.AuditEntryResponse;
import org.apache.avro.generic.GenericRecord;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * The audit log: what happened on the platform, and who did it.
 */
public interface AuditLogService {

    /** An event from another service (read as GenericRecord: any event type works). Duplicates are skipped. */
    void recordEvent(String topic, String key, GenericRecord event);

    /** A change made in this service (settings, feature flags, banners). Must run inside the change's transaction. */
    void recordChange(String eventType, String aggregateId, Long userId, String details);

    PageResponse<AuditEntryResponse> search(String eventType, String source, Long userId, Long storeId, String aggregateId,
                                            Instant from, Instant to, Pageable pageable);
}
