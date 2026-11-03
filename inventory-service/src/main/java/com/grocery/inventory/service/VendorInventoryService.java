package com.grocery.inventory.service;

import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.request.AddStockRequest;
import com.grocery.inventory.dto.request.SetStockRequest;
import com.grocery.inventory.dto.response.StockResponse;
import org.springframework.data.domain.Pageable;

/**
 * Stock management for the vendor's OWN store. Every method takes the vendor's storeId
 * and only ever touches stock of that store.
 */
public interface VendorInventoryService {

    PageResponse<StockResponse> listMyStock(Long storeId, boolean lowStockOnly, Pageable pageable);

    StockResponse getMyStock(Long storeId, Long productId);

    /** Sets the exact stock. The first call for a product creates its stock record. */
    StockResponse setStock(Long storeId, Long productId, SetStockRequest request);

    /** Adds received goods to an existing stock record. */
    StockResponse addStock(Long storeId, Long productId, AddStockRequest request);
}
