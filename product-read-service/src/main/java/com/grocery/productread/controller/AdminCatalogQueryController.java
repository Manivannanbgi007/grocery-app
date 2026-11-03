package com.grocery.productread.controller;

import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.CategoryResponse;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.service.CategoryQueryService;
import com.grocery.productread.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Admin panel: see every product of every store (for moderation) and every category.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN. Changes go to product-service.
 */
@Tag(name = "Admin - Catalog", description = "List all products and categories (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCatalogQueryController {

    private static final Set<ProductStatus> ALL_VISIBLE = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE, ProductStatus.BLOCKED);

    private final ProductQueryService productQueryService;
    private final CategoryQueryService categoryQueryService;

    @Operation(summary = "Search all products of all stores")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "NAME") ProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProductSearchFilter filter = new ProductSearchFilter(q, categoryId, storeId, null, null, null,
                status == null ? ALL_VISIBLE : Set.of(status), false, lowStockOnly);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully",
                productQueryService.searchProducts(filter, sort, page, size)));
    }

    @Operation(summary = "Get any product")
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", productQueryService.getProduct(productId)));
    }

    @Operation(summary = "All categories, also inactive ones")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success("Categories fetched successfully", categoryQueryService.getAllCategories()));
    }
}
