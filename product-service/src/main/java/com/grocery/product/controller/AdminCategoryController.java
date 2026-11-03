package com.grocery.product.controller;

import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.request.CategoryRequest;
import com.grocery.product.dto.request.UpdateCategoryStatusRequest;
import com.grocery.product.dto.response.CategoryResponse;
import com.grocery.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: manage the global product categories.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 * Listing categories (GET) is served by product-read-service.
 */
@Tag(name = "Admin - Categories", description = "Create and change product categories (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Create a category")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Category created successfully", category));
    }

    @Operation(summary = "Update a category")
    @PutMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable Long categoryId,
                                                                        @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", categoryService.updateCategory(categoryId, request)));
    }

    @Operation(summary = "Show or hide a category")
    @PatchMapping("/{categoryId}/status")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatus(@PathVariable Long categoryId,
                                                                              @Valid @RequestBody UpdateCategoryStatusRequest request) {
        CategoryResponse category = categoryService.updateCategoryStatus(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category status updated successfully", category));
    }
}
