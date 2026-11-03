package com.grocery.productread.controller;

import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service API. /internal/** is never routed by the API gateway.
 */
@Tag(name = "Internal", description = "For other microservices only - not reachable through the API gateway")
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductQueryService productQueryService;

    @Operation(summary = "Get a product in any status (used by inventory-service and cart-service)")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", productQueryService.getProduct(productId)));
    }

    @Operation(summary = "Get up to 100 products in one call, e.g. ?ids=1,2,3 (used by cart-service at checkout). Unknown ids are left out.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts(
            @RequestParam @NotEmpty @Size(max = 100) List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", productQueryService.getProducts(ids)));
    }
}
