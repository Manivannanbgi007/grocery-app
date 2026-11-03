package com.grocery.productread.messaging.consumer;

import com.grocery.productread.constant.KafkaTopics;
import com.grocery.productread.service.CatalogIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to every topic the read model is built from and hands each message to the indexing service:
 *   cdc.product_db.categories / cdc.product_db.products  Debezium change rows (field names = MySQL columns)
 *   grocery.inventory.events                             StockChanged from inventory-service
 * Every message arrives as a GenericRecord: Debezium rows have no generated class, and reading the
 * inventory events the same way keeps this service simple.
 *
 * ONE listener with ONE thread (concurrency = "1") on purpose: a product change, its category's change
 * and its stock change are applied one after the other, never at the same moment. Two threads could
 * otherwise each read the product document, change different fields, and the slower one would save
 * a copy without the other's change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogChangesConsumer {

    /** The listener's id: CatalogListenerControl pauses it by this name while scripts/reindex-products.ps1 copies an index. */
    public static final String LISTENER_ID = "catalog-changes";
    private static final String STOCK_CHANGED = "StockChanged";

    private final CatalogIndexService catalogIndexService;

    // idIsGroup = false: the Kafka consumer group stays "product-read-service" (application.yml), not the id. A new
    // group would start again from the oldest change.
    @KafkaListener(id = LISTENER_ID, idIsGroup = false,
            topics = {KafkaTopics.CDC_CATEGORIES, KafkaTopics.CDC_PRODUCTS, KafkaTopics.INVENTORY_EVENTS},
            concurrency = "1")
    public void onMessage(ConsumerRecord<String, GenericRecord> message) {
        GenericRecord value = message.value();
        if (value == null) {   // an empty "tombstone" message; nothing to apply
            return;
        }

        switch (message.topic()) {
            case KafkaTopics.CDC_CATEGORIES -> catalogIndexService.applyCategoryChange(value);
            case KafkaTopics.CDC_PRODUCTS -> catalogIndexService.applyProductChange(value);
            case KafkaTopics.INVENTORY_EVENTS -> onInventoryEvent(value);
            default -> log.warn("Ignoring a message from unexpected topic {}", message.topic());
        }
    }

    /** The inventory topic also carries events search does not need (LowStockDetected, ...): skip them. */
    private void onInventoryEvent(GenericRecord event) {
        if (STOCK_CHANGED.equals(event.getSchema().getName())) {
            catalogIndexService.applyStockChange(event);
        }
    }
}
