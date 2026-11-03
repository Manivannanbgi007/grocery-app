package com.grocery.productread.service;

import org.apache.avro.generic.GenericRecord;

/**
 * Keeps the Elasticsearch read model in sync with MySQL (product_db, one Debezium change at a time)
 * and with inventory-service's stock events.
 *
 * Every method is safe to call more than once with the same change (idempotent),
 * and an older change never overwrites a newer one (Elasticsearch external versions).
 */
public interface CatalogIndexService {

    /** A row of product_db.products was inserted, updated or deleted. */
    void applyProductChange(GenericRecord productRow);

    /** A row of product_db.categories changed. Also refreshes the category name stored on its products. */
    void applyCategoryChange(GenericRecord categoryRow);

    /** inventory-service published StockChanged. Stores the stock and copies it onto the product. */
    void applyStockChange(GenericRecord stockChanged);
}
