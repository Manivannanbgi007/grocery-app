package com.grocery.product.mapper;

import com.grocery.product.dto.request.CategoryRequest;
import com.grocery.product.dto.response.CategoryResponse;
import com.grocery.product.entity.Category;
import com.grocery.product.util.SlugUtils;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getDisplayOrder(),
                category.isActive());
    }

    public void copyDetails(CategoryRequest request, Category category) {
        category.setName(request.name().trim());
        category.setSlug(SlugUtils.toSlug(request.name()));
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }
}
