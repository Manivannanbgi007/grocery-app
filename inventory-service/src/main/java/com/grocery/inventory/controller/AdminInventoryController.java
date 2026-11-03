package com.grocery.inventory.controller;

import com.grocery.inventory.dto.common.ApiResponse;
import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.service.InventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: view the stock of every store (read only - stock belongs to the vendors).
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Inventory", description = "Stock across all stores (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryQueryService inventoryQueryService;

    @Operation(summary = "Search stock, optionally for one store and/or only low stock")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockResponse>>> searchStock(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @ParameterObject @PageableDefault(size = 20, sort = "productId") Pageable pageable) {
        PageResponse<StockResponse> stock = inventoryQueryService.searchStock(storeId, lowStockOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", stock));
    }

    @Operation(summary = "Get the stock of one product")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStock(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", inventoryQueryService.getStock(productId)));
    }
}
