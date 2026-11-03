package com.grocery.productread.support;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds inventory-service events as GenericRecords, with the same schema as
 * inventory-service/src/main/avro/produced/*.avsc.
 */
public final class InventoryEvents {

    private static final Schema TIMESTAMP_MILLIS = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));

    public static final Schema STOCK_CHANGED = SchemaBuilder.record("StockChanged").namespace("com.grocery.events.inventory").fields()
            .requiredString("eventId")
            .name("occurredAt").type(TIMESTAMP_MILLIS).noDefault()
            .requiredLong("productId")
            .requiredLong("storeId")
            .requiredInt("quantityOnHand")
            .requiredInt("reservedQuantity")
            .requiredInt("availableQuantity")
            .requiredInt("lowStockThreshold")
            .requiredBoolean("lowStock")
            .requiredLong("stockVersion")
            .endRecord();

    public static final Schema LOW_STOCK_DETECTED = SchemaBuilder.record("LowStockDetected").namespace("com.grocery.events.inventory").fields()
            .requiredString("eventId")
            .name("occurredAt").type(TIMESTAMP_MILLIS).noDefault()
            .requiredLong("productId")
            .requiredLong("storeId")
            .requiredInt("availableQuantity")
            .requiredInt("lowStockThreshold")
            .endRecord();

    private static final int THRESHOLD = 5;

    private InventoryEvents() {
    }

    /** StockChanged for a product with nothing reserved and the usual threshold of 5. */
    public static GenericRecord stockChanged(long productId, long storeId, int availableQuantity, long stockVersion) {
        GenericRecord event = new GenericData.Record(STOCK_CHANGED);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.now().toEpochMilli());
        event.put("productId", productId);
        event.put("storeId", storeId);
        event.put("quantityOnHand", availableQuantity);
        event.put("reservedQuantity", 0);
        event.put("availableQuantity", availableQuantity);
        event.put("lowStockThreshold", THRESHOLD);
        event.put("lowStock", availableQuantity <= THRESHOLD);
        event.put("stockVersion", stockVersion);
        return event;
    }

    public static GenericRecord lowStockDetected(long productId, long storeId, int availableQuantity) {
        GenericRecord event = new GenericData.Record(LOW_STOCK_DETECTED);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.now().toEpochMilli());
        event.put("productId", productId);
        event.put("storeId", storeId);
        event.put("availableQuantity", availableQuantity);
        event.put("lowStockThreshold", THRESHOLD);
        return event;
    }
}
