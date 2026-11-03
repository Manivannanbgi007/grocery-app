package com.grocery.inventory;

import com.grocery.events.inventory.StockChanged;
import com.grocery.inventory.client.ProductCatalogClient;
import com.grocery.inventory.client.dto.CatalogProduct;
import com.grocery.inventory.constant.HeaderNames;
import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.entity.OutboxEvent;
import com.grocery.inventory.repository.OutboxEventRepository;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end inside the service (real MySQL, product-read-service replaced by a mock):
 * a vendor sets and restocks a product, and the outbox holds the right events with the right versions.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")   // no Kafka needed here
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InventoryFlowIntegrationTest {

    private static final long STORE_ID = 50L;
    private static final long PRODUCT_ID = 501L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @Test
    void vendorSetsAndRestocksAProduct_andTheOutboxHoldsTheEvents() throws Exception {
        when(productCatalogClient.findProduct(PRODUCT_ID))
                .thenReturn(Optional.of(new CatalogProduct(PRODUCT_ID, STORE_ID, "Flow Test Product", "ACTIVE")));

        // 1. First stock for the product: 3 units, below the default threshold of 5
        mockMvc.perform(put("/api/v1/vendor/inventory/{productId}", PRODUCT_ID).header(HeaderNames.STORE_ID, STORE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantityOnHand": 3 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockThreshold").value(5))
                .andExpect(jsonPath("$.data.lowStock").value(true));

        // 2. Goods arrive
        mockMvc.perform(post("/api/v1/vendor/inventory/{productId}/additions", PRODUCT_ID).header(HeaderNames.STORE_ID, STORE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantity": 20 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityOnHand").value(23))
                .andExpect(jsonPath("$.data.lowStock").value(false));

        // 3. Another store's vendor cannot see it
        mockMvc.perform(get("/api/v1/vendor/inventory/{productId}", PRODUCT_ID).header(HeaderNames.STORE_ID, 51))
                .andExpect(status().isNotFound());

        // 4. Admins see it in the store's stock
        mockMvc.perform(get("/api/v1/admin/inventory").param("storeId", String.valueOf(STORE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].availableQuantity").value(23));

        // 5. Outbox: StockChanged for each change (version 0, then 1) + one LowStockDetected
        List<OutboxEvent> events = outboxEventRepository.findByAggregateTypeAndAggregateId("inventory", String.valueOf(PRODUCT_ID));
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("StockChanged", "LowStockDetected", "StockChanged");
        assertThat(stockChangedEvents(events))
                .extracting(StockChanged::getStockVersion, StockChanged::getAvailableQuantity, StockChanged::getLowStock)
                .containsExactlyInAnyOrder(tuple(0L, 3, true), tuple(1L, 23, false));

        // 6. The snapshot publishes the current stock again, with the same version
        mockMvc.perform(post("/internal/inventory/stock-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedEvents").value(1));
        List<OutboxEvent> afterSnapshot = outboxEventRepository.findByAggregateTypeAndAggregateId("inventory", String.valueOf(PRODUCT_ID));
        assertThat(stockChangedEvents(afterSnapshot))
                .extracting(StockChanged::getStockVersion)
                .containsExactlyInAnyOrder(0L, 1L, 1L);
    }

    /** Decodes the Avro payloads, exactly as a Kafka consumer would. */
    private List<StockChanged> stockChangedEvents(List<OutboxEvent> events) {
        try (KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer()) {
            deserializer.configure(Map.of("schema.registry.url", "mock://grocery-tests", "specific.avro.reader", true), false);
            return events.stream()
                    .filter(event -> event.getEventType().equals("StockChanged"))
                    .map(event -> (StockChanged) deserializer.deserialize(KafkaTopics.INVENTORY_EVENTS, event.getPayload()))
                    .toList();
        }
    }
}
