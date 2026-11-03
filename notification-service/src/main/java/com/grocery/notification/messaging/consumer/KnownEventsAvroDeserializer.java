package com.grocery.notification.messaging.consumer;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Reads Avro events from Kafka - but only the event types this service has a Java class for
 * (generated from src/main/avro). Any other event type becomes an {@link IgnoredEvent}.
 *
 * Why: one topic carries several event types (grocery.store.events has StoreRegistered,
 * StoreApproved, StoreStatusChanged ...). A service only copies the schemas it needs, and it
 * must keep working when a producer adds a new event type to the topic.
 *
 * Configured in application.yml (spring.kafka.consumer.properties.spring.deserializer.value.delegate.class).
 */
public class KnownEventsAvroDeserializer extends KafkaAvroDeserializer {

    private static final byte MAGIC_BYTE = 0x0;

    /** Used by Kafka, which creates the deserializer from configuration. */
    public KnownEventsAvroDeserializer() {
    }

    /** Used by tests with a mock Schema Registry. */
    public KnownEventsAvroDeserializer(SchemaRegistryClient schemaRegistryClient, Map<String, ?> config) {
        super(schemaRegistryClient, config);
    }

    @Override
    public Object deserialize(String topic, Headers headers, byte[] bytes) {
        Schema writerSchema = writerSchemaOf(bytes);
        if (writerSchema != null && SpecificData.get().getClass(writerSchema) == null) {
            return new IgnoredEvent(writerSchema.getFullName());
        }
        return super.deserialize(topic, headers, bytes);
    }

    /**
     * The message starts with [magic byte 0][4-byte schema id]; the schema itself is in Schema Registry.
     * Returns null for data that is not in this format - the normal deserializer then reports the error.
     */
    private Schema writerSchemaOf(byte[] bytes) {
        if (bytes == null || bytes.length < 5 || bytes[0] != MAGIC_BYTE) {
            return null;
        }
        int schemaId = ByteBuffer.wrap(bytes, 1, 4).getInt();
        try {
            ParsedSchema schema = schemaRegistry.getSchemaById(schemaId);
            return schema instanceof AvroSchema avroSchema ? avroSchema.rawSchema() : null;
        } catch (IOException | RestClientException ex) {
            throw new SerializationException("Cannot load schema id " + schemaId + " from Schema Registry", ex);
        }
    }
}
