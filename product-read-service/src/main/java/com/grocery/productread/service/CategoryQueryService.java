package com.grocery.productread.service;

import com.grocery.productread.dto.response.CategoryResponse;

import java.util.List;

/**
 * Category lookups - answered from Elasticsearch.
 */
public interface CategoryQueryService {

    /** Active categories in home-page order. */
    List<CategoryResponse> getActiveCategories();

    /** All categories, also inactive ones (admin panel). */
    List<CategoryResponse> getAllCategories();

    CategoryResponse getActiveCategory(Long categoryId);
}
