package com.grocery.productread.controller;

import com.grocery.productread.context.UserContextHolder;
import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
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
import java.util.Set;

/**
 * Store panel: list MY store's products in every status (the store comes from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR. Changes go to product-service.
 */
@Tag(name = "Vendor - Products", description = "List your store's products (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/products")
@RequiredArgsConstructor
public class VendorProductQueryController {

    /** Deleted products are removed from the index, so "all" means these three. */
    private static final Set<ProductStatus> ALL_VISIBLE = EnumSet.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE, ProductStatus.BLOCKED);

    private final ProductQueryService productQueryService;

    @Operation(summary = "List my products with their stock (optionally only one status, or only low stock)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listMyProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "NAME") ProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProductSearchFilter filter = new ProductSearchFilter(q, categoryId, UserContextHolder.getRequiredStoreId(), null, null, null,
                status == null ? ALL_VISIBLE : Set.of(status), false, lowStockOnly);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully",
                productQueryService.searchProducts(filter, sort, page, size)));
    }

    @Operation(summary = "Get one of my products")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getMyProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully",
                productQueryService.getStoreProduct(UserContextHolder.getRequiredStoreId(), productId)));
    }
}
