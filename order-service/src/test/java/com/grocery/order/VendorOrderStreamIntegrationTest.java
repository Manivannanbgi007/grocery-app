package com.grocery.order;

import com.grocery.order.client.CartClient;
import com.grocery.order.client.CustomerClient;
import com.grocery.order.client.StoreClient;
import com.grocery.order.constant.HeaderNames;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.live.OrderChangeListener;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.support.TestOrders;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * The vendor's live order stream with real MySQL and real Kafka: an order event on grocery.order.events (sent here
 * the way Debezium sends an outbox row) reaches the open stream of that order's store, and no other store's.
 * This checks the listener's own settings too: its consumer group, "newest events only", the value read as bytes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class VendorOrderStreamIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListeners;
    @Autowired
    private ConfluentKafkaContainer kafka;

    // The same mocks as OrderSagaIntegrationTest, so both use one Spring context (and one MySQL and Kafka)
    @MockitoBean
    private CartClient cartClient;
    @MockitoBean
    private CustomerClient customerClient;
    @MockitoBean
    private StoreClient storeClient;

    @Test
    void anOrderEvent_reachesTheStreamOfThatOrdersStore() throws Exception {
        Order order = orderRepository.save(TestOrders.pendingOrder(PaymentMethod.COD));   // Fresh Mart, store 1
        MvcResult freshMart = openStream(1L);
        MvcResult otherStore = openStream(2L);
        assertThat(content(freshMart)).startsWith("event:ready\ndata:{}\n\n");

        // The listener reads only events sent after it started: wait until it has its partition, then send the event
        // (again every 2 s until it is heard, in case it was sent a moment before the listener's starting point)
        ContainerTestUtils.waitForAssignment(kafkaListeners.getListenerContainer(OrderChangeListener.LISTENER_ID), 1);
        await().atMost(WAIT).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            sendOrderEvent(order.getId());
            assertThat(content(freshMart)).contains("event:order\ndata:{\"orderId\":" + order.getId() + ",\"status\":\"PENDING\"}");
        });
        assertThat(content(otherStore)).doesNotContain("event:order");   // (a keep-alive may have come meanwhile)
    }

    private MvcResult openStream(long storeId) throws Exception {
        return mockMvc.perform(get("/api/v1/vendor/orders/stream")
                        .header(HeaderNames.USER_ID, "2")
                        .header(HeaderNames.USER_ROLE, "VENDOR")
                        .header(HeaderNames.STORE_ID, String.valueOf(storeId))
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))   // as the browser asks
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String content(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    /** An order event the way Debezium sends it: key = the order id as text (the listener does not read the value). */
    private void sendOrderEvent(Long orderId) {
        try (Producer<String, byte[]> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(KafkaTopics.ORDER_EVENTS, String.valueOf(orderId), new byte[] {0}));
        }
    }
}
