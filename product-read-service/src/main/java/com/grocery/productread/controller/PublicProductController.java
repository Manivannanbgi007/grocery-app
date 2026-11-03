package com.grocery.productread.controller;

import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Customer panel: search and browse products. Public - no login needed.
 * Only ACTIVE products are ever returned here.
 */
@Tag(name = "Products", description = "Search and browse products (public)")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductQueryService productQueryService;

    @Operation(summary = "Search products by text, category, store, brand, price and stock, with sorting and paging")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @Parameter(description = "Search text, e.g. 'tomato' or 'amul'") @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @Parameter(description = "true = hide products that are out of stock") @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(defaultValue = "RELEVANCE") ProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProductSearchFilter filter = new ProductSearchFilter(q, categoryId, storeId, brand, minPrice, maxPrice,
                Set.of(ProductStatus.ACTIVE), inStockOnly, false);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully",
                productQueryService.searchProducts(filter, sort, page, size)));
    }

    /** "/suggestions" is matched before "/{productId}": Spring prefers a fixed path over a variable one. */
    @Operation(summary = "Suggestions while typing in the search box: also partial words ('toma'), any word order and small typos")
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> suggestProducts(
            @Parameter(description = "What the customer typed so far, e.g. 'toma' or 'amu but'") @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "6") @Min(1) @Max(10) int size) {
        return ResponseEntity.ok(ApiResponse.success("Suggestions fetched successfully",
                productQueryService.suggestProducts(q, categoryId, size)));
    }

    @Operation(summary = "Get one product")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully",
                productQueryService.getActiveProduct(productId)));
    }
}
