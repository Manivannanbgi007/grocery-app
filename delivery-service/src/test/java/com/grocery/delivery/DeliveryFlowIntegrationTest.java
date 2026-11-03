package com.grocery.delivery;

import com.grocery.delivery.constant.HeaderNames;
import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.entity.DeliveryZone;
import com.grocery.delivery.entity.OutboxEvent;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.repository.DeliveryZoneRepository;
import com.grocery.delivery.repository.OutboxEventRepository;
import com.grocery.events.order.OrderReadyForPickup;
import com.grocery.events.user.UserRegistered;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A partner registers, gets approved, goes online (Redis) and delivers an order that became ready for pickup
 * (real MySQL, Kafka and Redis).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@ActiveProfiles("test")
class DeliveryFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final long PARTNER = 950L;
    private static final long ORDER = 9500L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DeliveryZoneRepository zoneRepository;
    @Autowired
    private DeliveryPartnerRepository partnerRepository;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void newPartner_approved_online_deliversAnOrder() throws Exception {
        DeliveryZone zone = new DeliveryZone();
        zone.setName("Test Zone");
        zone.setCity("Bengaluru");
        zone.replacePincodes(Set.of("560099"));
        zone.setPartnerPayout(new BigDecimal("45.00"));
        zone.setActive(true);
        Long zoneId = zoneRepository.save(zone).getId();

        // 1. A delivery partner registers (UserRegistered) -> a profile with KYC NOT_SUBMITTED
        send(KafkaTopics.USER_EVENTS, UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setUserId(PARTNER).setFullName("Vijay Kumar").setEmail("vijay@example.com").setPhone("9845011111").setRole("DELIVERY_PARTNER")
                .build());
        await().atMost(WAIT).until(() -> partnerRepository.existsByUserId(PARTNER));

        // 2. KYC -> admin approves -> online
        mockMvc.perform(put("/api/v1/delivery/profile/kyc").header(HeaderNames.USER_ID, String.valueOf(PARTNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "vehicleType": "SCOOTER", "vehicleNumber": "KA03XY9999", "drivingLicenseNumber": "KA0320200011111", "zoneId": %d }
                                """.formatted(zoneId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"));
        mockMvc.perform(post("/api/v1/delivery/availability/online").header(HeaderNames.USER_ID, String.valueOf(PARTNER)))
                .andExpect(status().isBadRequest());   // not approved yet
        mockMvc.perform(post("/api/v1/admin/delivery-partners/{userId}/approve", PARTNER).header(HeaderNames.USER_ID, "2"))
                .andExpect(jsonPath("$.data.kycStatus").value("APPROVED"));
        mockMvc.perform(post("/api/v1/delivery/availability/online").header(HeaderNames.USER_ID, String.valueOf(PARTNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
        mockMvc.perform(get("/api/v1/delivery/availability").header(HeaderNames.USER_ID, String.valueOf(PARTNER)))
                .andExpect(jsonPath("$.data.online").value(true))
                .andExpect(jsonPath("$.data.onlineUntil").isNotEmpty());

        // 3. An order of a store in the zone is ready for pickup -> assigned to the online partner
        send(KafkaTopics.ORDER_EVENTS, OrderReadyForPickup.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(ORDER).setCustomerUserId(4L).setStoreId(1L)
                .setStoreName("Test Store").setStorePhone("9000000003").setStoreAddress("1 Test Road, Bengaluru 560099").setStorePincode("560099")
                .setDeliveryContactName("Priya Sharma").setDeliveryContactPhone("9000000004")
                .setDeliveryAddress("12, 5th Cross, Bengaluru 560099").setDeliveryPincode("560099")
                .setPaymentMethod("COD").setAmountToCollect("210.90")
                .build());
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(deliveryRepository.findByOrderId(ORDER)).get().extracting(Delivery::getStatus).isEqualTo(DeliveryStatus.ASSIGNED));
        Long deliveryId = deliveryRepository.findByOrderId(ORDER).orElseThrow().getId();

        // 4. The partner works through the steps
        for (String step : List.of("accept", "pick-up", "start", "deliver")) {
            mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/" + step, deliveryId).header(HeaderNames.USER_ID, String.valueOf(PARTNER)))
                    .andExpect(status().isOk());
        }
        Delivery delivered = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(eventTypes()).containsExactlyInAnyOrder("DeliveryAssigned", "DeliveryPickedUp", "DeliveryCompleted");

        // 5. Today's earnings include the zone payout
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        mockMvc.perform(get("/api/v1/delivery/earnings").header(HeaderNames.USER_ID, String.valueOf(PARTNER))
                        .param("from", today.toString()).param("to", today.toString()))
                .andExpect(jsonPath("$.data.deliveries").value(1))
                .andExpect(jsonPath("$.data.total").value(45.00));
        assertThat(partnerRepository.findByUserId(PARTNER)).get().extracting(DeliveryPartner::getKycStatus).isEqualTo(KycStatus.APPROVED);

        // 6. The customer rates the delivery; it counts for the partner's average
        mockMvc.perform(post("/api/v1/delivery-ratings").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": " + ORDER + ", \"rating\": 4, \"comment\": \"Polite and on time\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(4));
        mockMvc.perform(get("/api/v1/delivery/profile").header(HeaderNames.USER_ID, String.valueOf(PARTNER)))
                .andExpect(jsonPath("$.data.ratingAverage").value(4.0))
                .andExpect(jsonPath("$.data.ratingCount").value(1));
    }

    private List<String> eventTypes() {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(String.valueOf(ORDER)))
                .map(OutboxEvent::getEventType)
                .toList();
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
