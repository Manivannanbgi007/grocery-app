package com.grocery.product.controller;

import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.service.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: product moderation.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 * Listing all products (GET /api/v1/admin/products) is served by product-read-service.
 */
@Tag(name = "Admin - Products", description = "Block and unblock products (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @Operation(summary = "Block a product (hidden from customers; the vendor cannot undo it)")
    @PostMapping("/{productId}/block")
    public ResponseEntity<ApiResponse<ProductResponse>> blockProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product blocked", adminProductService.blockProduct(productId)));
    }

    @Operation(summary = "Unblock a product (it becomes ACTIVE again)")
    @PostMapping("/{productId}/unblock")
    public ResponseEntity<ApiResponse<ProductResponse>> unblockProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product unblocked", adminProductService.unblockProduct(productId)));
    }
}
