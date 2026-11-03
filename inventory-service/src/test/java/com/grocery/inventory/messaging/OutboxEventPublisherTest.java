package com.grocery.inventory.messaging;

import com.grocery.inventory.entity.OutboxEvent;
import com.grocery.inventory.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The outbox row keeps the current trace, so Debezium can send it on as the Kafka header "traceparent". */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private AvroEventSerializer avroEventSerializer;
    @Mock
    private Tracer tracer;

    private OutboxEventPublisher publisher;
    private SpecificRecordBase event;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(outboxEventRepository, avroEventSerializer, tracer);
        event = mock(SpecificRecordBase.class);   // any Avro event: only its id and name are read
        when(event.get("eventId")).thenReturn("event-1");
        when(event.getSchema()).thenReturn(Schema.createRecord("SomethingHappened", null, "com.grocery.events", false));
    }

    @Test
    void insideATrace_theRowKeepsTheTraceparent() {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(context.spanId()).thenReturn("00f067aa0ba902b7");
        when(context.sampled()).thenReturn(true);

        publisher.publish("order", 18L, event);

        assertThat(savedRow().getTraceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(savedRow().getAggregateId()).isEqualTo("18");
    }

    @Test
    void outsideATrace_theTraceparentIsEmpty() {
        when(tracer.currentSpan()).thenReturn(null);

        publisher.publish("order", 18L, event);

        assertThat(savedRow().getTraceparent()).isNull();
    }

    private OutboxEvent savedRow() {
        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(row.capture());
        return row.getValue();
    }
}
