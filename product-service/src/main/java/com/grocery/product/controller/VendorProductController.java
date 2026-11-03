package com.grocery.product.controller;

import com.grocery.product.context.UserContextHolder;
import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.request.ProductRequest;
import com.grocery.product.dto.request.UpdateProductStatusRequest;
import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.service.VendorProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Store panel: create and change the products of MY store (the store comes from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR.
 * Listing my products (GET /api/v1/vendor/products) is served by product-read-service.
 */
@Tag(name = "Vendor - Products", description = "Create and change your store's products (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/products")
@RequiredArgsConstructor
public class VendorProductController {

    private final VendorProductService vendorProductService;

    @Operation(summary = "Add a product to my store")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = vendorProductService.createProduct(UserContextHolder.getRequiredStoreId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Product created successfully", product));
    }

    @Operation(summary = "Update a product of my store")
    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable Long productId,
                                                                      @Valid @RequestBody ProductRequest request) {
        ProductResponse product = vendorProductService.updateProduct(UserContextHolder.getRequiredStoreId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    @Operation(summary = "Show (ACTIVE) or hide (INACTIVE) a product")
    @PatchMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProductStatus(@PathVariable Long productId,
                                                                            @Valid @RequestBody UpdateProductStatusRequest request) {
        ProductResponse product = vendorProductService.updateProductStatus(UserContextHolder.getRequiredStoreId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Product status updated successfully", product));
    }

    @Operation(summary = "Delete a product of my store")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long productId) {
        vendorProductService.deleteProduct(UserContextHolder.getRequiredStoreId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }

    @Operation(summary = "Upload a product image (JPEG, PNG or WEBP, max 2 MB, at most 5 per product)")
    @PostMapping(path = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> addImage(@PathVariable Long productId,
                                                                 @RequestPart("image") MultipartFile image) {
        ProductResponse product = vendorProductService.addImage(UserContextHolder.getRequiredStoreId(), productId, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Image uploaded successfully", product));
    }

    @Operation(summary = "Remove a product image")
    @DeleteMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<ProductResponse>> removeImage(@PathVariable Long productId,
                                                                    @RequestParam String imageUrl) {
        ProductResponse product = vendorProductService.removeImage(UserContextHolder.getRequiredStoreId(), productId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Image removed successfully", product));
    }
}
