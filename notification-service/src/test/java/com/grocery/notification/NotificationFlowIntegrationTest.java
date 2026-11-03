package com.grocery.notification;

import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.user.UserRegistered;
import com.grocery.notification.constant.HeaderNames;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.repository.ContactRepository;
import com.grocery.notification.repository.NotificationRepository;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Events on real Kafka become notifications in real MySQL and emails (the mail server is mocked):
 * a new customer and a new store, then an order between them.
 */
@SpringBootTest(properties = "management.health.mail.enabled=false")   // the mail server is a mock here, nothing to check
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final long CUSTOMER = 901L;
    private static final long VENDOR = 902L;
    private static final long STORE = 90L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void newCustomer_newStore_andAnOrderBetweenThem() throws Exception {
        send(KafkaTopics.USER_EVENTS, UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setUserId(CUSTOMER).setFullName("Neha Gupta").setEmail("neha@example.com").setPhone("9876543210").setRole("CUSTOMER")
                .build());
        send(KafkaTopics.USER_EVENTS, UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setUserId(VENDOR).setFullName("Imran Khan").setEmail("imran@example.com").setPhone("9876543211").setRole("VENDOR")
                .build());
        // Topics are read by different threads: like in real life, the store is approved after the vendor registered
        await().atMost(WAIT).until(() -> contactRepository.existsById(VENDOR));
        send(KafkaTopics.STORE_EVENTS, StoreApproved.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setStoreId(STORE).setOwnerUserId(VENDOR).setStoreName("Imran Fresh").setApprovedByUserId(2L)
                .build());
        await().atMost(WAIT).untilAsserted(() -> assertThat(notificationRepository.countByUserIdAndReadFalse(VENDOR)).isEqualTo(2));

        send(KafkaTopics.ORDER_EVENTS, OrderConfirmed.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(9001L).setCustomerUserId(CUSTOMER).setStoreId(STORE).setTotalAmount("320.00").setPaymentMethod("ONLINE")
                .build());

        // The customer: welcome + order confirmed; the vendor: welcome + store approved + new order
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(notificationRepository.countByUserIdAndReadFalse(CUSTOMER)).isEqualTo(2);
            assertThat(notificationRepository.countByUserIdAndReadFalse(VENDOR)).isEqualTo(3);
        });
        assertThat(contactRepository.findById(CUSTOMER)).get().extracting(contact -> contact.getEmail()).isEqualTo("neha@example.com");
        verify(mailSender, atLeast(5)).send(any(SimpleMailMessage.class));

        // The customer reads them in the app
        mockMvc.perform(get("/api/v1/notifications").header(HeaderNames.USER_ID, String.valueOf(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Order #9001 confirmed"))
                .andExpect(jsonPath("$.data.content[0].emailStatus").value("SENT"));
        mockMvc.perform(post("/api/v1/notifications/read-all").header(HeaderNames.USER_ID, String.valueOf(CUSTOMER)))
                .andExpect(jsonPath("$.data.count").value(2));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HeaderNames.USER_ID, String.valueOf(CUSTOMER)))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    private void send(String topic, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, "key", event));
        }
    }
}
