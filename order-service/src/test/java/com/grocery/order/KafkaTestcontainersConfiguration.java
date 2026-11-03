package com.grocery.order;

import com.grocery.order.constant.KafkaTopics;
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
	NewTopic inventoryEventsTopic() {
		return TopicBuilder.name(KafkaTopics.INVENTORY_EVENTS).partitions(1).build();
	}

	@Bean
	NewTopic paymentEventsTopic() {
		return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS).partitions(1).build();
	}

	/** Read by OrderChangeListener (the vendors' live order streams); in the tests the events are sent by hand. */
	@Bean
	NewTopic orderEventsTopic() {
		return TopicBuilder.name(KafkaTopics.ORDER_EVENTS).partitions(1).build();
	}

	@Bean
	NewTopic deliveryEventsTopic() {
		return TopicBuilder.name(KafkaTopics.DELIVERY_EVENTS).partitions(1).build();
	}

	@Bean
	NewTopic inventoryEventsDeadLetterTopic() {
		return TopicBuilder.name(KafkaTopics.INVENTORY_EVENTS + ".DLT").partitions(1).build();
	}

	@Bean
	NewTopic paymentEventsDeadLetterTopic() {
		return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS + ".DLT").partitions(1).build();
	}

}
