package com.grocery.platform;

import com.grocery.platform.constant.HeaderNames;
import com.grocery.platform.constant.KafkaTopics;
import com.grocery.platform.entity.AuditEntry;
import com.grocery.platform.entity.OutboxEvent;
import com.grocery.platform.repository.AuditEntryRepository;
import com.grocery.platform.repository.OutboxEventRepository;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MySQL + Kafka (Testcontainers): settings publish PlatformSettingsChanged, flags and banners work,
 * and events from other services land in the audit log exactly once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class PlatformFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private AuditEntryRepository auditEntryRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void superAdminChangesTheSettings_andTheChangeIsPublishedAndAudited() throws Exception {
        mockMvc.perform(get("/api/v1/platform/settings"))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.deliveryFee").value(30.00));

        mockMvc.perform(put("/api/v1/super-admin/settings").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "INR", "deliveryFee": 25.00, "freeDeliveryFrom": 399.00, "minimumOrderValue": 149.00,
                                  "supportEmail": "help@grocery.com", "supportPhone": "1800-000-1111" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryFee").value(25.00));

        assertThat(outboxEventRepository.findAll()).extracting(OutboxEvent::getEventType).contains("PlatformSettingsChanged");
        assertThat(auditEntryRepository.findAll()).extracting(AuditEntry::getEventType).contains("PlatformSettingsUpdated");

        // A minimum order above the free delivery amount makes no sense -> 400
        mockMvc.perform(put("/api/v1/super-admin/settings").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "INR", "deliveryFee": 25.00, "freeDeliveryFrom": 99.00, "minimumOrderValue": 149.00,
                                  "supportEmail": "help@grocery.com", "supportPhone": "1800-000-1111" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void featureFlagsAndBanners() throws Exception {
        mockMvc.perform(get("/api/v1/platform/feature-flags")).andExpect(jsonPath("$.data.DELIVERY_SLOTS").value(false));
        mockMvc.perform(patch("/api/v1/super-admin/feature-flags/DELIVERY_SLOTS").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true}"))
                .andExpect(jsonPath("$.data.enabled").value(true));
        mockMvc.perform(get("/api/v1/platform/feature-flags")).andExpect(jsonPath("$.data.DELIVERY_SLOTS").value(true));
        mockMvc.perform(post("/api/v1/super-admin/feature-flags").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\": \"city pune\", \"description\": \"x\", \"enabled\": false}"))
                .andExpect(status().isBadRequest());

        createBanner("Mango season", null, null).andExpect(status().isCreated());
        createBanner("Last year's sale", "2025-01-01T00:00:00Z", "2025-02-01T00:00:00Z").andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/banners"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Mango season"));
        mockMvc.perform(get("/api/v1/admin/banners").header(HeaderNames.USER_ID, "2"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void eventsOfOtherServices_areAuditedOnce() throws Exception {
        Schema schema = SchemaBuilder.record("OrderCancelled").namespace("com.grocery.events.order").fields()
                .requiredString("eventId").requiredLong("occurredAt").requiredLong("orderId")
                .requiredLong("customerUserId").requiredLong("storeId").requiredString("reason")
                .endRecord();
        GenericRecord event = new GenericData.Record(schema);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.parse("2026-10-26T10:00:00Z").toEpochMilli());
        event.put("orderId", 77L);
        event.put("customerUserId", 4L);
        event.put("storeId", 1L);
        event.put("reason", "Changed my mind");

        send(KafkaTopics.ORDER_EVENTS, "77", event);
        send(KafkaTopics.ORDER_EVENTS, "77", event);   // Kafka may deliver twice

        await().atMost(WAIT).untilAsserted(() -> mockMvc.perform(get("/api/v1/super-admin/audit-log")
                        .header(HeaderNames.USER_ID, "1").param("eventType", "OrderCancelled").param("aggregateId", "77"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(4))
                .andExpect(jsonPath("$.data.content[0].storeId").value(1))
                .andExpect(jsonPath("$.data.content[0].occurredAt").value("2026-10-26T10:00:00Z")));
    }

    private ResultActions createBanner(String title, String startsAt, String endsAt) throws Exception {
        String window = startsAt == null ? "" : ", \"startsAt\": \"%s\", \"endsAt\": \"%s\"".formatted(startsAt, endsAt);
        return mockMvc.perform(post("/api/v1/admin/banners").header(HeaderNames.USER_ID, "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"%s\", \"imageUrl\": \"http://img/x.jpg\", \"position\": 1, \"active\": true%s}"
                        .formatted(title, window)));
    }

    private void send(String topic, String key, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, key, event));
        }
    }
}
