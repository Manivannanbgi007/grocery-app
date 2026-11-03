package com.grocery.inventory.controller;

import com.grocery.inventory.context.UserContextHolder;
import com.grocery.inventory.dto.common.ApiResponse;
import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.request.AddStockRequest;
import com.grocery.inventory.dto.request.SetStockRequest;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.service.VendorInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store panel: the logged-in vendor manages the stock of THEIR OWN store (store id from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - Inventory", description = "Stock of your store's products (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/inventory")
@RequiredArgsConstructor
public class VendorInventoryController {

    private final VendorInventoryService vendorInventoryService;

    @Operation(summary = "List my store's stock (lowStockOnly=true for items that need restocking)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockResponse>>> listMyStock(
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @ParameterObject @PageableDefault(size = 20, sort = "productId") Pageable pageable) {
        PageResponse<StockResponse> stock = vendorInventoryService.listMyStock(UserContextHolder.getRequiredStoreId(), lowStockOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", stock));
    }

    @Operation(summary = "Get the stock of one of my products")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> getMyStock(@PathVariable Long productId) {
        StockResponse stock = vendorInventoryService.getMyStock(UserContextHolder.getRequiredStoreId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Stock fetched successfully", stock));
    }

    @Operation(summary = "Set the exact stock of my product (the first call creates its stock record)")
    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> setStock(@PathVariable Long productId,
                                                               @Valid @RequestBody SetStockRequest request) {
        StockResponse stock = vendorInventoryService.setStock(UserContextHolder.getRequiredStoreId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Stock saved successfully", stock));
    }

    @Operation(summary = "Add received goods to my product's stock")
    @PostMapping("/{productId}/additions")
    public ResponseEntity<ApiResponse<StockResponse>> addStock(@PathVariable Long productId,
                                                               @Valid @RequestBody AddStockRequest request) {
        StockResponse stock = vendorInventoryService.addStock(UserContextHolder.getRequiredStoreId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Stock added successfully", stock));
    }
}
