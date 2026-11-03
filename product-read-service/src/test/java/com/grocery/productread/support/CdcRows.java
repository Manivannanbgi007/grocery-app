package com.grocery.productread.support;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.time.Instant;

/**
 * Builds change rows exactly like the ones Debezium writes to cdc.product_db.* (same field names
 * and types, see product-service/debezium/product-cdc-connector.json): decimals as strings,
 * timestamps as epoch millis, the image list as a JSON string, BOOLEAN as 1/0.
 */
public final class CdcRows {

    public static final Schema PRODUCT_ROW = SchemaBuilder.record("Value").namespace("cdc.product_db.products").fields()
            .requiredLong("id")
            .requiredLong("store_id")
            .requiredLong("category_id")
            .requiredString("name")
            .optionalString("description")
            .optionalString("brand")
            .requiredString("sku")
            .requiredString("unit")
            .requiredString("unit_quantity")
            .requiredString("price")
            .requiredString("discount_percentage")
            .requiredString("selling_price")
            .requiredString("tax_percentage")
            .requiredString("image_urls")
            .requiredString("status")
            .requiredLong("created_at")
            .requiredLong("updated_at")
            .requiredLong("version")
            .optionalString("__deleted")
            .endRecord();

    public static final Schema CATEGORY_ROW = SchemaBuilder.record("Value").namespace("cdc.product_db.categories").fields()
            .requiredLong("id")
            .requiredString("name")
            .requiredString("slug")
            .optionalString("description")
            .optionalString("image_url")
            .requiredInt("display_order")
            .requiredInt("active")
            .requiredLong("created_at")
            .requiredLong("updated_at")
            .requiredLong("version")
            .optionalString("__deleted")
            .endRecord();

    private CdcRows() {
    }

    /** A product row; change the fields you care about with row.put(...). */
    public static GenericRecord product(long id, long storeId, long categoryId, String name, String sellingPrice, long version) {
        long now = Instant.now().toEpochMilli();
        GenericRecord row = new GenericData.Record(PRODUCT_ROW);
        row.put("id", id);
        row.put("store_id", storeId);
        row.put("category_id", categoryId);
        row.put("name", name);
        row.put("description", name + " description");
        row.put("brand", null);
        row.put("sku", "SKU-" + id);
        row.put("unit", "KG");
        row.put("unit_quantity", "1.000");
        row.put("price", sellingPrice);
        row.put("discount_percentage", "0.00");
        row.put("selling_price", sellingPrice);
        row.put("tax_percentage", "0.00");
        row.put("image_urls", "[]");
        row.put("status", "ACTIVE");
        row.put("created_at", now);
        row.put("updated_at", now);
        row.put("version", version);
        row.put("__deleted", "false");
        return row;
    }

    public static GenericRecord category(long id, String name, boolean active, long version) {
        long now = Instant.now().toEpochMilli();
        GenericRecord row = new GenericData.Record(CATEGORY_ROW);
        row.put("id", id);
        row.put("name", name);
        row.put("slug", name.toLowerCase().replace(' ', '-'));
        row.put("description", null);
        row.put("image_url", null);
        row.put("display_order", (int) id);
        row.put("active", active ? 1 : 0);   // MySQL BOOLEAN = TINYINT(1), as Debezium's snapshot sends it
        row.put("created_at", now);
        row.put("updated_at", now);
        row.put("version", version);
        row.put("__deleted", "false");
        return row;
    }
}
