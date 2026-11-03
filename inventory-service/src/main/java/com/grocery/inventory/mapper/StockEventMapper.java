package com.grocery.inventory.mapper;

import com.grocery.events.inventory.LowStockDetected;
import com.grocery.events.inventory.StockChanged;
import com.grocery.inventory.entity.Stock;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes. Every event gets a new unique eventId.
 */
@Component
public class StockEventMapper {

    /**
     * The stock must be flushed to the database first, so getVersion() is the new version
     * (consumers use stockVersion to ignore old events).
     */
    public StockChanged toStockChanged(Stock stock) {
        return StockChanged.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setProductId(stock.getProductId())
                .setStoreId(stock.getStoreId())
                .setQuantityOnHand(stock.getQuantityOnHand())
                .setReservedQuantity(stock.getReservedQuantity())
                .setAvailableQuantity(stock.getAvailableQuantity())
                .setLowStockThreshold(stock.getLowStockThreshold())
                .setLowStock(stock.isLowStock())
                .setStockVersion(stock.getVersion())
                .build();
    }

    public LowStockDetected toLowStockDetected(Stock stock) {
        return LowStockDetected.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setProductId(stock.getProductId())
                .setStoreId(stock.getStoreId())
                .setAvailableQuantity(stock.getAvailableQuantity())
                .setLowStockThreshold(stock.getLowStockThreshold())
                .build();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
