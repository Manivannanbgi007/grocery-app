package com.grocery.productread.controller;

import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.dto.response.CategoryResponse;
import com.grocery.productread.service.CategoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer panel: the category list on the home page. Public - no login needed.
 */
@Tag(name = "Categories", description = "Browse product categories (public)")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final CategoryQueryService categoryQueryService;

    @Operation(summary = "Active categories in home-page order")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success("Categories fetched successfully", categoryQueryService.getActiveCategories()));
    }

    @Operation(summary = "Get one active category")
    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success("Category fetched successfully", categoryQueryService.getActiveCategory(categoryId)));
    }
}
