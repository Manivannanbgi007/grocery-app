package com.grocery.auth;

import com.grocery.auth.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throw-away Kafka broker in Docker for tests that need Kafka.
 * Same image as infrastructure/docker-compose.yml. (Schema Registry is replaced by "mock://" in application-test.yml.)
 *
 * The topics are created up front - like infrastructure/kafka/create-topics.sh does locally - because a
 * consumer that subscribes to a topic which does not exist yet only notices it minutes later.
 * Spring creates NewTopic beans before the @KafkaListener containers start.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	ConfluentKafkaContainer kafkaContainer() {
		return new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.2.4"));
	}

	@Bean
	NewTopic storeEventsTopic() {
		return TopicBuilder.name(KafkaTopics.STORE_EVENTS).partitions(1).build();
	}

	@Bean
	NewTopic storeEventsDeadLetterTopic() {
		return TopicBuilder.name(KafkaTopics.STORE_EVENTS + ".DLT").partitions(1).build();
	}

}
