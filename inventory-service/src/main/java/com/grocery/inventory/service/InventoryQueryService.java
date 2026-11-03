package com.grocery.inventory.service;

import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.response.StockResponse;
import org.springframework.data.domain.Pageable;

/**
 * Read-only stock queries across all stores (admins and other services).
 */
public interface InventoryQueryService {

    /** storeId is optional: null = every store. */
    PageResponse<StockResponse> searchStock(Long storeId, boolean lowStockOnly, Pageable pageable);

    StockResponse getStock(Long productId);
}
