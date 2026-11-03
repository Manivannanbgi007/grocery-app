package com.grocery.inventory.mapper;

import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.entity.Stock;
import org.springframework.stereotype.Component;

@Component
public class StockMapper {

    public StockResponse toResponse(Stock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getStoreId(),
                stock.getQuantityOnHand(),
                stock.getReservedQuantity(),
                stock.getAvailableQuantity(),
                stock.getLowStockThreshold(),
                stock.isLowStock(),
                stock.isOutOfStock(),
                stock.getUpdatedAt());
    }
}
