package com.grocery.order;

import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.inventory.UnavailableItem;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.order.client.CartClient;
import com.grocery.order.client.CustomerClient;
import com.grocery.order.client.StoreClient;
import com.grocery.order.constant.HeaderNames;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.entity.Order;
import com.grocery.order.entity.OutboxEvent;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.grocery.order.support.TestOrders.freshMart;
import static com.grocery.order.support.TestOrders.homeAddress;
import static com.grocery.order.support.TestOrders.readyCheckout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The order side of the saga with real MySQL and real Kafka (the other services are mocked or simulated):
 * place an order -> PaymentConfirmed arrives -> CONFIRMED -> the store accepts it;
 * place another -> StockReservationFailed arrives -> CANCELLED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class OrderSagaIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private org.testcontainers.kafka.ConfluentKafkaContainer kafka;

    @MockitoBean
    private CartClient cartClient;
    @MockitoBean
    private CustomerClient customerClient;
    @MockitoBean
    private StoreClient storeClient;

    @BeforeEach
    void setUp() {
        when(cartClient.getCheckoutSummary(4L)).thenReturn(readyCheckout());
        when(customerClient.findAddress(4L, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));
    }

    @Test
    void paymentConfirmed_confirmsTheOrder_andTheStoreCanAcceptIt() throws Exception {
        Long orderId = placeOrder();
        assertThat(eventTypes(orderId)).containsExactly("OrderCreated");

        PaymentConfirmed confirmed = PaymentConfirmed.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setPaymentId(1L).setOrderId(orderId).setCustomerUserId(4L).setAmount("158.00").setPaymentMethod("COD")
                .build();
        send(KafkaTopics.PAYMENT_EVENTS, orderId, confirmed);
        send(KafkaTopics.PAYMENT_EVENTS, orderId, confirmed);   // a duplicate must not confirm twice

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId)).get().extracting(Order::getStatus).isEqualTo(OrderStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/vendor/orders/{id}/accept", orderId).header(HeaderNames.STORE_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.timeline.length()").value(3));
        assertThat(eventTypes(orderId)).containsExactlyInAnyOrder("OrderCreated", "OrderConfirmed", "OrderAccepted");
    }

    @Test
    void stockReservationFailed_cancelsTheOrder() throws Exception {
        Long orderId = placeOrder();

        send(KafkaTopics.INVENTORY_EVENTS, orderId, StockReservationFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setStoreId(1L).setReason("Not enough stock")
                .setItems(List.of(new UnavailableItem(7L, 1, 0)))
                .build());

        await().atMost(WAIT).untilAsserted(() -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatusReason()).isEqualTo("Not enough stock: Amul Butter (sold out)");
        });
        assertThat(eventTypes(orderId)).containsExactlyInAnyOrder("OrderCreated", "OrderCancelled");
    }

    private Long placeOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "COD" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private List<String> eventTypes(Long orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(String.valueOf(orderId)))
                .map(OutboxEvent::getEventType)
                .toList();
    }

    /** Sends an event the way Debezium does: key = orderId (text), Avro value registered in Schema Registry. */
    private void send(String topic, Long orderId, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, String.valueOf(orderId), event));
        }
    }
}
