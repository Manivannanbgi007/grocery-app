package com.grocery.platform.messaging;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Turns an Avro event into bytes in the Confluent format: [magic byte][schema id][Avro data].
 *
 * The first time an event type is serialized, its schema is registered in Schema Registry,
 * which rejects changes that would break existing consumers (BACKWARD compatibility).
 *
 * Subject name = "<topic>-<record name>" (TopicRecordNameStrategy), because one topic
 * carries several event types, e.g. grocery.user.events-com.grocery.events.user.UserRegistered.
 */
@Component
public class AvroEventSerializer {

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();

    public AvroEventSerializer(@Value("${spring.kafka.properties.schema.registry.url}") String schemaRegistryUrl) {
        serializer.configure(Map.of(
                "schema.registry.url", schemaRegistryUrl,
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
    }

    public byte[] serialize(String topic, SpecificRecord event) {
        return serializer.serialize(topic, event);
    }
}
