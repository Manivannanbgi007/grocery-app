package com.grocery.productread.messaging.consumer;

import com.grocery.productread.ElasticsearchTestcontainersConfiguration;
import com.grocery.productread.KafkaTestcontainersConfiguration;
import com.grocery.productread.constant.KafkaTopics;
import com.grocery.productread.repository.ProductDocumentRepository;
import com.grocery.productread.support.CdcRows;
import com.grocery.productread.support.InventoryEvents;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole read pipeline minus Debezium: change rows on the cdc.* topics and stock events on
 * grocery.inventory.events (real Kafka) end up in Elasticsearch (real Elasticsearch).
 */
@SpringBootTest
@Import({ElasticsearchTestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class CatalogChangesConsumerIntegrationTest {

    @Autowired
    private ProductDocumentRepository productRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;
    @Autowired
    private CatalogListenerControl listenerControl;

    @Test
    void changeRowsOnKafka_endUpInElasticsearch() {
        send(KafkaTopics.CDC_CATEGORIES, "7", CdcRows.category(7, "Beverages", true, 0));
        send(KafkaTopics.CDC_PRODUCTS, "70", CdcRows.product(70, 1, 7, "Mango Juice", "99.00", 0));
        send(KafkaTopics.CDC_PRODUCTS, "70", CdcRows.product(70, 1, 7, "Mango Juice 1L", "95.00", 1));
        send(KafkaTopics.INVENTORY_EVENTS, "70", InventoryEvents.lowStockDetected(70, 1, 3));   // not needed for search: skipped
        send(KafkaTopics.INVENTORY_EVENTS, "70", InventoryEvents.stockChanged(70, 1, 3, 2));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(productRepository.findById(70L)).hasValueSatisfying(juice -> {
                    assertThat(juice.getName()).isEqualTo("Mango Juice 1L");
                    assertThat(juice.getSellingPrice()).isEqualByComparingTo("95.00");
                    assertThat(juice.getCategoryName()).isEqualTo("Beverages");
                    assertThat(juice.getAvailableQuantity()).isEqualTo(3);
                    assertThat(juice.isInStock()).isTrue();
                    assertThat(juice.isLowStock()).isTrue();
                }));
    }

    @Test
    void aPausedListenerAppliesNothing_andCatchesUpAfterResume() throws InterruptedException {
        // First a change that is applied: the listener has joined its consumer group and has its partitions
        send(KafkaTopics.CDC_PRODUCTS, "71", CdcRows.product(71, 1, 7, "Apple Juice", "80.00", 0));
        await().atMost(Duration.ofSeconds(60)).until(() -> productRepository.findById(71L).isPresent());

        assertThat(listenerControl.pause().paused()).isTrue();
        try {
            send(KafkaTopics.CDC_PRODUCTS, "72", CdcRows.product(72, 1, 7, "Guava Juice", "85.00", 0));
            // While paused (what scripts/reindex-products.ps1 does while it copies an index), the change waits in Kafka
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> productRepository.findById(72L).isEmpty());
        } finally {
            assertThat(listenerControl.resume().paused()).isFalse();
        }
        await().atMost(Duration.ofSeconds(30)).until(() -> productRepository.findById(72L).isPresent());
    }

    /**
     * Sends a message the way Debezium does: key = id (text), value = Avro registered in Schema Registry.
     * TopicRecordNameStrategy, like the services' outbox serializer: one topic can hold several event types.
     */
    private void send(String topic, String key, Object row) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, key, row));
        }
    }
}
