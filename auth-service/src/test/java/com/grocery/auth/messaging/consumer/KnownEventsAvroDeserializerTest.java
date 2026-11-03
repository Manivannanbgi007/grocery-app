package com.grocery.auth.messaging.consumer;

import com.grocery.events.store.StoreApproved;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast unit test with an in-memory Schema Registry - no Kafka needed.
 */
class KnownEventsAvroDeserializerTest {

    private static final String TOPIC = "grocery.store.events";

    private final MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    private final Map<String, Object> config = Map.of(
            "schema.registry.url", "mock://deserializer-test",
            "specific.avro.reader", true,
            "value.subject.name.strategy", TopicRecordNameStrategy.class.getName());

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer(schemaRegistry, config);
    private final KnownEventsAvroDeserializer deserializer = new KnownEventsAvroDeserializer(schemaRegistry, config);

    @Test
    void eventWithAGeneratedClass_isReadAsThatClass() {
        StoreApproved event = StoreApproved.newBuilder()
                .setEventId("event-1")
                .setOccurredAt(Instant.parse("2026-10-25T10:00:00Z"))
                .setStoreId(5L)
                .setOwnerUserId(10L)
                .setStoreName("Daily Needs")
                .setApprovedByUserId(2L)
                .build();

        Object result = deserializer.deserialize(TOPIC, new RecordHeaders(), serializer.serialize(TOPIC, event));

        assertThat(result).isInstanceOf(StoreApproved.class);
        assertThat(((StoreApproved) result).getStoreId()).isEqualTo(5L);
    }

    @Test
    void eventWithoutAGeneratedClass_becomesIgnoredEvent() {
        // auth-service has no class for StoreRegistered, so it must be skipped - not fail
        Schema storeRegistered = SchemaBuilder.record("StoreRegistered").namespace("com.grocery.events.store")
                .fields().requiredString("eventId").requiredLong("storeId").endRecord();
        GenericRecord event = new GenericData.Record(storeRegistered);
        event.put("eventId", "event-2");
        event.put("storeId", 5L);

        Object result = deserializer.deserialize(TOPIC, new RecordHeaders(), serializer.serialize(TOPIC, event));

        assertThat(result).isEqualTo(new IgnoredEvent("com.grocery.events.store.StoreRegistered"));
    }
}
