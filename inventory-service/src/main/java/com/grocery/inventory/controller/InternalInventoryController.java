package com.grocery.inventory.controller;

import com.grocery.inventory.dto.common.ApiResponse;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.dto.response.StockSnapshotResponse;
import com.grocery.inventory.service.InventoryQueryService;
import com.grocery.inventory.service.StockSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service and maintenance API. /internal/** is never routed by the API gateway,
 * so only other services (and developers on their machine) can call it.
 */
@Tag(name = "Internal", description = "For other microservices and maintenance only - not reachable through the API gateway")
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final StockSnapshotService stockSnapshotService;

    @Operation(summary = "Get the stock of a product (404 = the product has no stock record)")
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStock(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", inventoryQueryService.getStock(productId)));
    }

    @Operation(summary = "Publish StockChanged again for every product (after loading seed data or rebuilding a read model)")
    @PostMapping("/stock-snapshot")
    public ResponseEntity<ApiResponse<StockSnapshotResponse>> publishSnapshot() {
        StockSnapshotResponse snapshot = stockSnapshotService.publishSnapshot();
        return ResponseEntity.ok(ApiResponse.success("Stock snapshot published to the outbox", snapshot));
    }
}
