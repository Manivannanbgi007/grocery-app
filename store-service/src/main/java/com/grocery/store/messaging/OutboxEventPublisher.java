package com.grocery.store.messaging;

import com.grocery.store.constant.KafkaTopics;
import com.grocery.store.entity.OutboxEvent;
import com.grocery.store.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Publishes a domain event using the TRANSACTIONAL OUTBOX pattern.
 *
 * The event is NOT sent to Kafka here. It is saved in the outbox_event table, in the same
 * database transaction as the business change (e.g. the new user). So either both are saved, or neither.
 * Debezium then reads the new row from the MySQL binlog and publishes it to Kafka
 * (topic grocery.<aggregateType>.events, see debezium/*.json).
 *
 * Usage, inside a @Transactional service method:
 *   outboxEventPublisher.publish(KafkaTopics.USER_AGGREGATE, user.getId(), userEventMapper.toUserRegistered(user));
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final AvroEventSerializer avroEventSerializer;
    private final Tracer tracer;

    /**
     * @param aggregateType "user", "store", "order" ... (decides the topic)
     * @param aggregateId   id of the changed record (becomes the Kafka message key)
     * @param event         Avro event; every event has an "eventId" field
     */
    @Transactional(propagation = Propagation.MANDATORY)   // fails if called outside a transaction
    public void publish(String aggregateType, Object aggregateId, SpecificRecordBase event) {
        String eventId = String.valueOf(event.get("eventId"));
        String eventType = event.getSchema().getName();
        byte[] payload = avroEventSerializer.serialize(KafkaTopics.forAggregate(aggregateType), event);

        outboxEventRepository.save(new OutboxEvent(
                eventId, aggregateType, String.valueOf(aggregateId), eventType, payload, Instant.now(), currentTraceparent()));

        log.info("Outbox: saved {} eventId={} for {} id={}", eventType, eventId, aggregateType, aggregateId);
    }

    /**
     * The current trace as a W3C "traceparent" value: 00-&lt;trace id&gt;-&lt;span id&gt;-&lt;01 = sampled&gt;.
     * Debezium copies it into the Kafka header "traceparent"; Spring Kafka reads it in the consumer and continues the
     * trace, so Jaeger shows the HTTP request and the events it caused as one trace. Null outside a trace.
     */
    private String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null || span.isNoop()) {
            return null;
        }
        TraceContext context = span.context();
        return "00-%s-%s-%s".formatted(context.traceId(), context.spanId(), Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }
}
