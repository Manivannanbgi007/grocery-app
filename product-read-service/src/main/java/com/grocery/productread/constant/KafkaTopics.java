package com.grocery.productread.constant;

/**
 * Kafka topics this service reads (see docs/event-catalog.md).
 *
 * The cdc.* topics are written by Debezium (product-service/debezium/product-cdc-connector.json):
 * one message per changed row of product_db, key = the row id.
 *
 * grocery.inventory.events holds inventory-service's domain events (key = productId).
 * This service only uses StockChanged (contract: inventory-service/src/main/avro/produced/StockChanged.avsc).
 */
public final class KafkaTopics {

    public static final String CDC_PRODUCTS = "cdc.product_db.products";
    public static final String CDC_CATEGORIES = "cdc.product_db.categories";
    public static final String INVENTORY_EVENTS = "grocery.inventory.events";

    private KafkaTopics() {
    }
}
