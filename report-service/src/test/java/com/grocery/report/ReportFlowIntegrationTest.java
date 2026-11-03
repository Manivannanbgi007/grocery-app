package com.grocery.report;

import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.StockChanged;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderLine;
import com.grocery.events.order.OrderRejected;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.user.UserRegistered;
import com.grocery.report.constant.HeaderNames;
import com.grocery.report.constant.KafkaTopics;
import com.grocery.report.entity.OrderFact;
import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.repository.OrderFactRepository;
import com.grocery.report.repository.PartnerFactRepository;
import com.grocery.report.repository.StockFactRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MySQL + Kafka (Testcontainers): events of six topics build the read model, the reports read it.
 *
 *   order 1001: ONLINE 150.00 (items 120.00, commission 10 %) -> paid -> confirmed -> DELIVERED
 *   order 1002: COD 90.00 -> confirmed -> CANCELLED
 *   order 1003: ONLINE 60.00 -> paid -> confirmed -> REJECTED (refund not done yet)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class ReportFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final long STORE = 101L;
    private static final long CUSTOMER = 900L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderFactRepository orderFactRepository;
    @Autowired
    private StockFactRepository stockFactRepository;
    @Autowired
    private PartnerFactRepository partnerFactRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    /** One "now" for all events, so the delivery time is exactly 30 minutes. */
    private final Instant now = Instant.now();

    @Test
    void eventsBuildTheReadModel_andTheReportsAddUp() throws Exception {
        String today = now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString();

        send(KafkaTopics.USER_EVENTS, UserRegistered.newBuilder().setEventId(id()).setOccurredAt(now)
                .setUserId(CUSTOMER).setFullName("Report Tester").setEmail("rt@example.com").setPhone("9876543210").setRole("CUSTOMER").build());
        send(KafkaTopics.STORE_EVENTS, StoreApproved.newBuilder().setEventId(id()).setOccurredAt(now)
                .setStoreId(STORE).setOwnerUserId(950L).setStoreName("Report Mart").setApprovedByUserId(2L).build());
        send(KafkaTopics.DELIVERY_EVENTS, PartnerKycReviewed.newBuilder().setEventId(id()).setOccurredAt(now)
                .setPartnerUserId(901L).setFullName("Ravi Rider").setStatus("APPROVED").build());
        send(KafkaTopics.INVENTORY_EVENTS, StockChanged.newBuilder().setEventId(id()).setOccurredAt(now)
                .setProductId(11L).setStoreId(STORE).setQuantityOnHand(0).setReservedQuantity(0).setAvailableQuantity(0)
                .setLowStockThreshold(5).setLowStock(true).setStockVersion(3L).build());

        // Payment events may arrive before OrderCreated (another topic): the consumer retries until the order exists
        send(KafkaTopics.PAYMENT_EVENTS, paymentConfirmed(1001L, "ONLINE"));
        send(KafkaTopics.ORDER_EVENTS, orderCreated(1001L, "ONLINE", "120.00", "30.00", "150.00",
                List.of(new OrderLine(11L, "Banana", 2, "50.00"), new OrderLine(12L, "Bread", 1, "20.00"))));
        send(KafkaTopics.ORDER_EVENTS, OrderConfirmed.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(1001L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setTotalAmount("150.00").setPaymentMethod("ONLINE").build());
        send(KafkaTopics.ORDER_EVENTS, OrderDelivered.newBuilder().setEventId(id()).setOccurredAt(now.plusSeconds(1800)).setOrderId(1001L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setTotalAmount("150.00").setPaymentMethod("ONLINE").build());

        send(KafkaTopics.ORDER_EVENTS, orderCreated(1002L, "COD", "60.00", "30.00", "90.00", List.of(new OrderLine(12L, "Bread", 3, "20.00"))));
        send(KafkaTopics.PAYMENT_EVENTS, paymentConfirmed(1002L, "COD"));
        send(KafkaTopics.ORDER_EVENTS, OrderConfirmed.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(1002L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setTotalAmount("90.00").setPaymentMethod("COD").build());
        send(KafkaTopics.ORDER_EVENTS, OrderCancelled.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(1002L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setReason("Changed my mind").setCancelledBy("CUSTOMER").build());

        send(KafkaTopics.ORDER_EVENTS, orderCreated(1003L, "ONLINE", "60.00", "0.00", "60.00", List.of(new OrderLine(13L, "Milk", 2, "30.00"))));
        send(KafkaTopics.PAYMENT_EVENTS, paymentConfirmed(1003L, "ONLINE"));
        send(KafkaTopics.ORDER_EVENTS, OrderConfirmed.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(1003L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setTotalAmount("60.00").setPaymentMethod("ONLINE").build());
        send(KafkaTopics.ORDER_EVENTS, OrderRejected.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(1003L)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setReason("Out of milk").build());

        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(orderFactRepository.findById(1001L)).get().extracting(OrderFact::getStatus).isEqualTo(ReportOrderStatus.DELIVERED);
            assertThat(orderFactRepository.findById(1002L)).get().extracting(OrderFact::getStatus).isEqualTo(ReportOrderStatus.CANCELLED);
            assertThat(orderFactRepository.findById(1003L)).get().extracting(OrderFact::getStatus).isEqualTo(ReportOrderStatus.REJECTED);
            assertThat(orderFactRepository.findById(1003L).orElseThrow().getPaymentStatus().name()).isEqualTo("PAID");
            assertThat(stockFactRepository.count()).isEqualTo(1);
            assertThat(partnerFactRepository.count()).isEqualTo(1);
        });

        mockMvc.perform(get("/api/v1/admin/reports/dashboard").header(HeaderNames.USER_ID, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(3))
                .andExpect(jsonPath("$.data.ordersToday").value(3))
                .andExpect(jsonPath("$.data.salesToday").value(150.00))
                .andExpect(jsonPath("$.data.activeStores").value(1))
                .andExpect(jsonPath("$.data.activeDeliveryPartners").value(1))
                .andExpect(jsonPath("$.data.totalCustomers").value(1))
                .andExpect(jsonPath("$.data.activeCustomers").value(1))
                .andExpect(jsonPath("$.data.pendingRefunds").value(1))
                .andExpect(jsonPath("$.data.outOfStockProducts").value(1))
                .andExpect(jsonPath("$.data.averageDeliveryMinutes").value(30));

        mockMvc.perform(get("/api/v1/admin/reports/sales/daily").header(HeaderNames.USER_ID, "2")
                        .param("from", today).param("to", today))
                .andExpect(jsonPath("$.data.days[0].orders").value(3))
                .andExpect(jsonPath("$.data.days[0].cancelledOrders").value(2))
                .andExpect(jsonPath("$.data.days[0].salesOrders").value(1))
                .andExpect(jsonPath("$.data.totalSales").value(150.00));

        mockMvc.perform(get("/api/v1/admin/reports/top-products").header(HeaderNames.USER_ID, "2")
                        .param("from", today).param("to", today))
                .andExpect(jsonPath("$.data.length()").value(2))   // only the delivered order counts
                .andExpect(jsonPath("$.data[0].productName").value("Banana"))
                .andExpect(jsonPath("$.data[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].sales").value(100.00));

        mockMvc.perform(get("/api/v1/admin/reports/top-stores").header(HeaderNames.USER_ID, "2")
                        .param("from", today).param("to", today))
                .andExpect(jsonPath("$.data[0].storeName").value("Report Mart"))
                .andExpect(jsonPath("$.data[0].sales").value(150.00));

        mockMvc.perform(get("/api/v1/vendor/reports/sales/daily").header(HeaderNames.USER_ID, "950")
                        .header(HeaderNames.STORE_ID, String.valueOf(STORE)).param("from", today).param("to", today))
                .andExpect(jsonPath("$.data.storeId").value(STORE))
                .andExpect(jsonPath("$.data.totalOrders").value(3));

        String deliveredDay = now.plusSeconds(1800).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString();
        mockMvc.perform(get("/api/v1/super-admin/reports/commissions").header(HeaderNames.USER_ID, "1")
                        .param("from", today).param("to", deliveredDay))
                .andExpect(jsonPath("$.data.stores[0].deliveredOrders").value(1))
                .andExpect(jsonPath("$.data.stores[0].itemsTotal").value(120.00))
                .andExpect(jsonPath("$.data.stores[0].commission").value(12.00))
                .andExpect(jsonPath("$.data.stores[0].storePayout").value(108.00));

        mockMvc.perform(get("/api/v1/admin/reports/sales/daily").header(HeaderNames.USER_ID, "2")
                        .param("from", "2026-12-31").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports/top-products").header(HeaderNames.USER_ID, "2")
                        .param("from", today).param("to", today).param("limit", "500"))
                .andExpect(status().isBadRequest());
    }

    private OrderCreated orderCreated(long orderId, String method, String itemsTotal, String deliveryFee, String total,
                                      List<OrderLine> lines) {
        return OrderCreated.newBuilder().setEventId(id()).setOccurredAt(now).setOrderId(orderId)
                .setCustomerUserId(CUSTOMER).setStoreId(STORE).setItems(lines).setItemsTotal(itemsTotal)
                .setCouponCode(null).setCouponDiscount("0.00").setDeliveryFee(deliveryFee).setTotalAmount(total)
                .setPaymentMethod(method).setCommissionPercentage("10.00")
                .build();
    }

    private PaymentConfirmed paymentConfirmed(long orderId, String method) {
        return PaymentConfirmed.newBuilder().setEventId(id()).setOccurredAt(now).setPaymentId(orderId)
                .setOrderId(orderId).setCustomerUserId(CUSTOMER).setAmount("1.00").setPaymentMethod(method).build();
    }

    private String id() {
        return UUID.randomUUID().toString();
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
