package com.grocery.auth.messaging.consumer;

import com.grocery.auth.KafkaTestcontainersConfiguration;
import com.grocery.auth.TestcontainersConfiguration;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.repository.ProcessedEventRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.events.store.StoreApproved;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Kafka (Testcontainers) + real MySQL: sends events to grocery.store.events exactly
 * as Debezium would, and checks what auth-service does with them.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class StoreEventsConsumerIntegrationTest {

    private static final String TOPIC = "grocery.store.events";
    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void storeApproved_linksTheStoreToTheVendor_andADuplicateIsHandledOnce() {
        User vendor = userRepository.save(vendor("approved.vendor@example.com", "9700000001"));
        StoreApproved event = storeApproved(41L, vendor.getId());

        sendAvro("41", event);
        sendAvro("41", event);   // Kafka may deliver the same event twice

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(userRepository.findById(vendor.getId()).orElseThrow().getStoreId()).isEqualTo(41L));
        assertThat(processedEventRepository.findById(event.getEventId())).isPresent();
    }

    @Test
    void eventTypeWithoutAClass_isSkipped_andLaterEventsAreStillHandled() {
        User vendor = userRepository.save(vendor("skip.vendor@example.com", "9700000002"));
        String ignoredEventId = UUID.randomUUID().toString();

        sendAvro("42", storeRegistered(ignoredEventId, 42L));   // auth-service has no class for this
        sendAvro("42", storeApproved(42L, vendor.getId()));      // same key = same partition = handled after it

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(userRepository.findById(vendor.getId()).orElseThrow().getStoreId()).isEqualTo(42L));
        assertThat(processedEventRepository.findById(ignoredEventId)).isEmpty();
    }

    @Test
    void unreadableMessage_goesToTheDeadLetterTopic() {
        byte[] garbage = "this is not an Avro event".getBytes(StandardCharsets.UTF_8);
        try (Producer<String, byte[]> producer = new KafkaProducer<>(producerConfig(), new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(TOPIC, "43", garbage));
        }

        try (Consumer<String, byte[]> deadLetterConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-checker",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new ByteArrayDeserializer())) {
            deadLetterConsumer.subscribe(List.of(TOPIC + ".DLT"));

            ConsumerRecord<String, byte[]> deadLetter = KafkaTestUtils.getSingleRecord(deadLetterConsumer, TOPIC + ".DLT", WAIT);
            assertThat(deadLetter.value()).isEqualTo(garbage);
        }
    }

    // ----- helpers -----

    private void sendAvro(String key, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of(
                "schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);

        try (Producer<String, Object> producer = new KafkaProducer<>(producerConfig(), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(TOPIC, key, event));
        }
    }

    private Map<String, Object> producerConfig() {
        return Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    }

    private StoreApproved storeApproved(long storeId, long ownerUserId) {
        return StoreApproved.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setStoreId(storeId)
                .setOwnerUserId(ownerUserId)
                .setStoreName("Test Store " + storeId)
                .setApprovedByUserId(2L)
                .build();
    }

    /** A store-service event type that auth-service does not have a generated class for. */
    private GenericRecord storeRegistered(String eventId, long storeId) {
        Schema schema = SchemaBuilder.record("StoreRegistered").namespace("com.grocery.events.store")
                .fields().requiredString("eventId").requiredLong("storeId").endRecord();
        GenericRecord record = new GenericData.Record(schema);
        record.put("eventId", eventId);
        record.put("storeId", storeId);
        return record;
    }

    private User vendor(String email, String phone) {
        User user = new User();
        user.setFullName("Test Vendor");
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.VENDOR);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
