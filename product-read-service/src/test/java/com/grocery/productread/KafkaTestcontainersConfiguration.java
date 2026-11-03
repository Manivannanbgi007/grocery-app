package com.grocery.productread;

import com.grocery.productread.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throw-away Kafka broker in Docker for tests that need Kafka.
 * The topics are created up front, like infrastructure/kafka/create-topics.sh does locally.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	ConfluentKafkaContainer kafkaContainer() {
		return new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.2.4"));
	}

	@Bean
	NewTopic productChangesTopic() {
		return TopicBuilder.name(KafkaTopics.CDC_PRODUCTS).partitions(1).build();
	}

	@Bean
	NewTopic categoryChangesTopic() {
		return TopicBuilder.name(KafkaTopics.CDC_CATEGORIES).partitions(1).build();
	}

	@Bean
	NewTopic inventoryEventsTopic() {
		return TopicBuilder.name(KafkaTopics.INVENTORY_EVENTS).partitions(1).build();
	}

}
