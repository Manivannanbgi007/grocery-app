package com.grocery.customer;

import com.grocery.customer.constant.HeaderNames;
import com.grocery.customer.constant.KafkaTopics;
import com.grocery.customer.repository.CustomerRepository;
import com.grocery.events.user.UserRegistered;
import com.jayway.jsonpath.JsonPath;
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
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end with real MySQL and Kafka (Testcontainers):
 * a UserRegistered event (exactly as Debezium publishes it) creates the profile,
 * then the customer manages addresses through the REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class CustomerFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void registeredCustomer_getsAProfile_andManagesAddresses() throws Exception {
        // 1. auth-service publishes UserRegistered for a new customer (user 31)
        send("31", userRegistered(31L, "CUSTOMER"));
        await().atMost(WAIT).until(() -> customerRepository.existsByUserId(31L));

        // 2. The customer sees the profile created from the event
        mockMvc.perform(get("/api/v1/customers/me").header(HeaderNames.USER_ID, "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user31@example.com"))
                .andExpect(jsonPath("$.data.addressCount").value(0));

        // 3. First address -> default; second address with setAsDefault -> the new default
        String home = addAddress("31", "HOME", false);
        String work = addAddress("31", "WORK", true);
        assertThat((Boolean) JsonPath.read(home, "$.data.defaultAddress")).isTrue();
        assertThat((Boolean) JsonPath.read(work, "$.data.defaultAddress")).isTrue();
        Integer homeId = JsonPath.read(home, "$.data.id");
        Integer workId = JsonPath.read(work, "$.data.id");

        // 4. order-service can read the address through the internal API
        mockMvc.perform(get("/internal/customers/31/addresses/{id}", workId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("WORK"));

        // 5. Deleting the default makes the remaining address the default
        mockMvc.perform(delete("/api/v1/customers/me/addresses/{id}", workId).header(HeaderNames.USER_ID, "31"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/me/addresses").header(HeaderNames.USER_ID, "31"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(homeId))
                .andExpect(jsonPath("$.data[0].defaultAddress").value(true));

        // 6. Another user cannot see this address
        mockMvc.perform(get("/internal/customers/4/addresses/{id}", homeId)).andExpect(status().isNotFound());
    }

    @Test
    void vendorRegistration_andOtherUserEvents_doNotCreateProfiles() {
        // Another user event type customer-service has no class for (like PasswordResetRequested)
        Schema schema = SchemaBuilder.record("PasswordResetRequested").namespace("com.grocery.events.user")
                .fields().requiredString("eventId").requiredLong("userId").endRecord();
        GenericRecord passwordReset = new GenericData.Record(schema);
        passwordReset.put("eventId", UUID.randomUUID().toString());
        passwordReset.put("userId", 32L);

        send("32", passwordReset);
        send("32", userRegistered(32L, "VENDOR"));
        send("33", userRegistered(33L, "CUSTOMER"));   // proves the consumer kept going

        await().atMost(WAIT).until(() -> customerRepository.existsByUserId(33L));
        assertThat(customerRepository.existsByUserId(32L)).isFalse();
    }

    private String addAddress(String userId, String label, boolean setAsDefault) throws Exception {
        return mockMvc.perform(post("/api/v1/customers/me/addresses").header(HeaderNames.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "label": "%s", "contactName": "Test User", "contactPhone": "9000000031",
                                  "addressLine1": "1 Test Street", "city": "Bengaluru", "state": "Karnataka",
                                  "pincode": "560038", "setAsDefault": %s }
                                """.formatted(label, setAsDefault)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private UserRegistered userRegistered(long userId, String role) {
        return UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setUserId(userId)
                .setFullName("User " + userId)
                .setEmail("user" + userId + "@example.com")
                .setPhone("9000000031")
                .setRole(role)
                .build();
    }

    private void send(String key, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of(
                "schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);

        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(KafkaTopics.USER_EVENTS, key, event));
        }
    }
}
