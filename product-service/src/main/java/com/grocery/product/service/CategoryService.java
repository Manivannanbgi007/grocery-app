package com.grocery.product.service;

import com.grocery.product.dto.request.CategoryRequest;
import com.grocery.product.dto.request.UpdateCategoryStatusRequest;
import com.grocery.product.dto.response.CategoryResponse;

/**
 * Admin panel: manage the global product categories. (Listing them is done by product-read-service.)
 */
public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);

    CategoryResponse updateCategoryStatus(Long categoryId, UpdateCategoryStatusRequest request);
}
