package com.grocery.platform.service.impl;

import com.grocery.platform.dto.common.PageResponse;
import com.grocery.platform.dto.response.AuditEntryResponse;
import com.grocery.platform.entity.AuditEntry;
import com.grocery.platform.mapper.PlatformMapper;
import com.grocery.platform.repository.AuditEntryRepository;
import com.grocery.platform.service.AuditLogService;
import com.grocery.platform.service.ProcessedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    static final String THIS_SERVICE = "platform-service";

    /** Fields that name the user an event is about, in order of preference. */
    private static final List<String> USER_FIELDS = List.of("customerUserId", "partnerUserId", "ownerUserId", "userId",
            "changedByUserId", "approvedByUserId");

    private final AuditEntryRepository auditEntryRepository;
    private final ProcessedEventService processedEventService;
    private final PlatformMapper platformMapper;

    @Override
    @Transactional
    public void recordEvent(String topic, String key, GenericRecord event) {
        String eventId = text(event, "eventId");
        String eventType = event.getSchema().getName();
        if (eventId == null || !processedEventService.markAsProcessed(eventId, eventType)) {
            return;
        }
        auditEntryRepository.save(AuditEntry.of(eventId, eventType, topic, key, firstLong(event, USER_FIELDS),
                firstLong(event, List.of("storeId")), event.toString(), occurredAt(event)));
        log.debug("Audited {} {} key={}", topic, eventType, key);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(String eventType, String aggregateId, Long userId, String details) {
        auditEntryRepository.save(AuditEntry.of(UUID.randomUUID().toString(), eventType, THIS_SERVICE, aggregateId,
                userId, null, details, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(String eventType, String source, Long userId, Long storeId, String aggregateId,
                                                   Instant from, Instant to, Pageable pageable) {
        return PageResponse.from(auditEntryRepository.search(eventType, source, userId, storeId, aggregateId, from, to, pageable)
                .map(platformMapper::toResponse));
    }

    private String text(GenericRecord event, String field) {
        Object value = event.hasField(field) ? event.get(field) : null;
        return value == null ? null : value.toString();   // Avro text can be a Utf8 object, not a String
    }

    private Long firstLong(GenericRecord event, List<String> fields) {
        for (String field : fields) {
            if (event.hasField(field) && event.get(field) instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    /** timestamp-millis arrives as a long (or an Instant, depending on the Avro settings). */
    private Instant occurredAt(GenericRecord event) {
        Object value = event.hasField("occurredAt") ? event.get("occurredAt") : null;
        if (value instanceof Instant instant) {
            return instant;
        }
        return value instanceof Long millis ? Instant.ofEpochMilli(millis) : Instant.now();
    }
}
