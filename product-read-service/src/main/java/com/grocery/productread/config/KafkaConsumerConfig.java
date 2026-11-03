package com.grocery.productread.config;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.generic.IndexedRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * What happens when applying a change to Elasticsearch fails.
 *
 *   1. Retry 10 times with growing pauses (1s, 2s, 4s ... at most 30s between tries, about 3 minutes in total).
 *      Here the usual cause is Elasticsearch being briefly unavailable, so we wait longer than other services
 *      do before giving up - a skipped change would leave the search index out of date.
 *   2. Still failing? The change is copied to the dead-letter topic "<topic>.DLT" and the consumer moves on.
 *   Messages that cannot even be read go to the DLT straight away.
 *
 * Spring Boot plugs the DefaultErrorHandler bean into every @KafkaListener automatically.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        // Failed record -> same partition of "<topic>.DLT" (the DLT topics are created by create-topics.sh)
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate,
                (failedRecord, exception) -> new TopicPartition(failedRecord.topic() + ".DLT", failedRecord.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxAttempts(10);
        return new DefaultErrorHandler(deadLetterRecoverer, backOff);
    }

    /**
     * Producer used ONLY to write failed messages to the dead-letter topic:
     *   - a message that could not be read      -> its original bytes, unchanged
     *   - a change our code failed to apply     -> the Avro record again (generic or generated class)
     */
    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate(KafkaProperties kafkaProperties,
                                                                 KafkaConnectionDetails kafkaConnectionDetails) {
        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                IndexedRecord.class, new KafkaAvroSerializer()), true);

        // The producer passes this config to both serializers.
        // KafkaConnectionDetails = the Kafka address Spring Boot resolved (application.yml, or Testcontainers in tests).
        // Delivery: acks=all (every in-sync replica has the dead letter), idempotent (a retry cannot write it twice),
        // retries for up to 2 minutes. These are Kafka's defaults today; they are written here because a lost dead
        // letter is a lost event. If the DLT cannot be written at all, the error handler does not skip the event:
        // Kafka delivers it again (tested in inventory-service, KafkaConsumerConfigTest).
        Map<String, Object> producerConfig = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectionDetails.getProducer().getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000,
                "schema.registry.url", kafkaProperties.getProperties().get("schema.registry.url"),
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName());

        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerConfig, new StringSerializer(), valueSerializer));
    }
}
