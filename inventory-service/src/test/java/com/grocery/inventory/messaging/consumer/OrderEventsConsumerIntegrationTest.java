package com.grocery.inventory.messaging.consumer;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderLine;
import com.grocery.inventory.KafkaTestcontainersConfiguration;
import com.grocery.inventory.TestcontainersConfiguration;
import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.entity.OutboxEvent;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.entity.StockReservation;
import com.grocery.inventory.enums.ReservationStatus;
import com.grocery.inventory.messaging.StockEventPublisher;
import com.grocery.inventory.repository.OutboxEventRepository;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.repository.StockReservationRepository;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Inventory's part of the saga with real MySQL and real Kafka:
 * OrderCreated reserves (all or nothing), OrderCancelled releases, OrderDelivered commits.
 * Also the safety nets: an event that keeps failing ends in the dead-letter topic, a failed change leaves no event.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class OrderEventsConsumerIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final long STORE = 70L;

    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockReservationRepository reservationRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private StockEventPublisher stockEventPublisher;

    @Test
    void reserve_thenCancel_givesTheUnitsBack() {
        stockRepository.save(Stock.create(7001L, STORE, 10, 2));

        send(7001L, orderCreated(7001L, 7001L, 4));
        await().atMost(WAIT).untilAsserted(() -> assertThat(available(7001L)).isEqualTo(6));
        assertThat(eventTypes(7001L)).contains("StockReserved");

        send(7001L, OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(7001L).setCustomerUserId(4L).setStoreId(STORE)
                .setReason("Changed my mind").setCancelledBy("CUSTOMER")
                .build());
        await().atMost(WAIT).untilAsserted(() -> assertThat(available(7001L)).isEqualTo(10));
        assertThat(reservationRepository.findByOrderId(7001L)).get()
                .extracting(StockReservation::getStatus).isEqualTo(ReservationStatus.RELEASED);
        assertThat(eventTypes(7001L)).contains("StockReserved", "StockReleased");
    }

    @Test
    void notEnoughStock_reservesNothing() {
        stockRepository.save(Stock.create(7002L, STORE, 3, 2));

        send(7002L, orderCreated(7002L, 7002L, 5));

        await().atMost(WAIT).untilAsserted(() -> assertThat(eventTypes(7002L)).containsExactly("StockReservationFailed"));
        assertThat(available(7002L)).isEqualTo(3);
        assertThat(reservationRepository.findByOrderId(7002L)).isEmpty();
    }

    @Test
    void delivery_removesTheUnitsFromTheShelf() {
        stockRepository.save(Stock.create(7003L, STORE, 10, 2));
        send(7003L, orderCreated(7003L, 7003L, 4));
        send(7003L, OrderDelivered.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(7003L).setCustomerUserId(4L).setStoreId(STORE)
                .setTotalAmount("130.00").setPaymentMethod("COD")
                .build());

        await().atMost(WAIT).untilAsserted(() -> {
            Stock stock = stockRepository.findByProductId(7003L).orElseThrow();
            assertThat(stock.getQuantityOnHand()).isEqualTo(6);
            assertThat(stock.getReservedQuantity()).isZero();
        });
    }

    /**
     * An event that fails every time (here its id is longer than the 36 characters processed_event allows, so MySQL
     * refuses it) is tried 4 times, then copied to grocery.order.events.DLT with the reason in Kafka headers, and the
     * consumer moves on. Nothing is reserved.
     */
    @Test
    void anEventThatKeepsFailing_endsInTheDeadLetterTopicWithTheReason() {
        stockRepository.save(Stock.create(7004L, STORE, 10, 2));
        OrderCreated badEvent = orderCreated(7004L, 7004L, 1);
        badEvent.setEventId("an-event-id-that-is-longer-than-36-characters");

        send(7004L, badEvent);

        ConsumerRecord<String, byte[]> deadLetter = await().atMost(WAIT).until(() -> deadLetter("7004"), Objects::nonNull);
        assertThat(header(deadLetter, "kafka_dlt-original-topic")).isEqualTo(KafkaTopics.ORDER_EVENTS);
        assertThat(header(deadLetter, "kafka_dlt-exception-fqcn")).isNotBlank();
        assertThat(deadLetter.headers().lastHeader("kafka_dlt-original-offset")).isNotNull();
        assertThat(available(7004L)).isEqualTo(10);
        assertThat(reservationRepository.findByOrderId(7004L)).isEmpty();
    }

    /** The transactional outbox: a change and its event are saved together, or not at all. */
    @Test
    void aChangeThatFails_leavesNoEventBehind() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Stock stock = stockRepository.save(Stock.create(7005L, STORE, 10, 2));
            stockEventPublisher.publishChange(stock, false);   // the outbox row is written ...
            throw new IllegalStateException("a later step of the change fails");
        })).hasMessage("a later step of the change fails");

        assertThat(stockRepository.findByProductId(7005L)).isEmpty();   // ... and rolled back with the change
        assertThat(eventTypes(7005L)).isEmpty();
    }

    /** The dead letter with this key (order id), or null while there is none. */
    private ConsumerRecord<String, byte[]> deadLetter(String key) {
        TopicPartition deadLetterTopic = new TopicPartition(KafkaTopics.ORDER_EVENTS + ".DLT", 0);
        try (Consumer<String, byte[]> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.assign(List.of(deadLetterTopic));
            consumer.seekToBeginning(List.of(deadLetterTopic));
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofSeconds(2))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
            return null;
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private int available(long productId) {
        return stockRepository.findByProductId(productId).orElseThrow().getAvailableQuantity();
    }

    private List<String> eventTypes(long orderId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(String.valueOf(orderId)))
                .map(OutboxEvent::getEventType)
                .toList();
    }

    private OrderCreated orderCreated(long orderId, long productId, int quantity) {
        return OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerUserId(4L).setStoreId(STORE)
                .setItems(List.of(new OrderLine(productId, "Test product", quantity, "10.00")))
                .setItemsTotal("40.00").setCouponCode(null).setCouponDiscount("0.00").setDeliveryFee("30.00")
                .setTotalAmount("70.00").setPaymentMethod("COD")
                .build();
    }

    /** Sends an order event the way Debezium does: key = orderId (text), Avro value. */
    private void send(long orderId, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(KafkaTopics.ORDER_EVENTS, String.valueOf(orderId), event));
        }
    }
}
