package com.grocery.payment;

import com.grocery.events.inventory.ReservedItem;
import com.grocery.events.inventory.StockReserved;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderLine;
import com.grocery.payment.constant.HeaderNames;
import com.grocery.payment.constant.KafkaTopics;
import com.grocery.payment.entity.OutboxEvent;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.repository.OutboxEventRepository;
import com.grocery.payment.repository.PaymentRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payment side of the saga with real MySQL and real Kafka:
 * OrderCreated -> StockReserved -> the customer pays (mock) -> the order is cancelled -> refund.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void onlinePayment_isPaid_andRefundedWhenTheOrderIsCancelled() throws Exception {
        long orderId = 501L;
        send(KafkaTopics.ORDER_EVENTS, orderId, orderCreated(orderId, "ONLINE"));
        send(KafkaTopics.INVENTORY_EVENTS, orderId, stockReserved(orderId));
        awaitStatus(orderId, PaymentStatus.AWAITING_PAYMENT);

        mockMvc.perform(post("/api/v1/payments/orders/{orderId}/mock-pay", orderId).header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "success": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        send(KafkaTopics.ORDER_EVENTS, orderId, OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerUserId(4L).setStoreId(1L)
                .setReason("Ordered by mistake").setCancelledBy("CUSTOMER")
                .build());
        awaitStatus(orderId, PaymentStatus.REFUNDED);

        assertThat(eventTypes(orderId)).containsExactly("PaymentConfirmed", "RefundCompleted");
    }

    @Test
    void stockReservedArrivingBeforeOrderCreated_isRetriedUntilThePaymentExists() {
        long orderId = 502L;
        send(KafkaTopics.INVENTORY_EVENTS, orderId, stockReserved(orderId));   // too early: no payment yet
        send(KafkaTopics.ORDER_EVENTS, orderId, orderCreated(orderId, "COD"));

        awaitStatus(orderId, PaymentStatus.CASH_ON_DELIVERY);
        assertThat(eventTypes(orderId)).containsExactly("PaymentConfirmed");
    }

    private void awaitStatus(long orderId, PaymentStatus expected) {
        await().atMost(WAIT).untilAsserted(() ->
                assertThat(paymentRepository.findByOrderId(orderId)).get().extracting(Payment::getStatus).isEqualTo(expected));
    }

    private List<String> eventTypes(long orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(String.valueOf(orderId)))
                .sorted((first, second) -> first.getCreatedAt().compareTo(second.getCreatedAt()))
                .map(OutboxEvent::getEventType)
                .toList();
    }

    private OrderCreated orderCreated(long orderId, String paymentMethod) {
        return OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerUserId(4L).setStoreId(1L)
                .setItems(List.of(new OrderLine(1L, "Tomato", 2, "36.00")))
                .setItemsTotal("72.00").setCouponCode(null).setCouponDiscount("0.00").setDeliveryFee("30.00")
                .setTotalAmount("102.00").setPaymentMethod(paymentMethod)
                .build();
    }

    private StockReserved stockReserved(long orderId) {
        return StockReserved.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setStoreId(1L)
                .setItems(List.of(new ReservedItem(1L, 2)))
                .build();
    }

    private void send(String topic, long orderId, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, String.valueOf(orderId), event));
        }
    }
}
