package com.grocery.platform.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Producer of the dead-letter replay (DeadLetterServiceImpl): sends a failed message back to its topic as raw bytes,
 * exactly as the consumer received it. acks=all and idempotent, like every producer that must not lose an event.
 */
@Configuration
public class DeadLetterKafkaConfig {

    @Bean
    public KafkaTemplate<String, byte[]> replayKafkaTemplate(KafkaConnectionDetails kafkaConnectionDetails) {
        Map<String, Object> producerConfig = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectionDetails.getProducer().getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerConfig, new StringSerializer(), new ByteArraySerializer()));
    }
}
