package com.grocery.product.service.impl;

import com.grocery.product.dto.request.CategoryRequest;
import com.grocery.product.dto.request.UpdateCategoryStatusRequest;
import com.grocery.product.dto.response.CategoryResponse;
import com.grocery.product.entity.Category;
import com.grocery.product.exception.DuplicateResourceException;
import com.grocery.product.exception.ResourceNotFoundException;
import com.grocery.product.mapper.CategoryMapper;
import com.grocery.product.repository.CategoryRepository;
import com.grocery.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("A category with this name already exists: " + request.name());
        }
        Category category = new Category();
        categoryMapper.copyDetails(request, category);
        category.setActive(true);
        Category savedCategory = categoryRepository.save(category);

        log.info("Created category id={} name={}", savedCategory.getId(), savedCategory.getName());
        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), categoryId)) {
            throw new DuplicateResourceException("A category with this name already exists: " + request.name());
        }
        categoryMapper.copyDetails(request, category);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategoryStatus(Long categoryId, UpdateCategoryStatusRequest request) {
        Category category = findCategory(categoryId);
        category.setActive(request.active());
        log.info("Category id={} active={}", categoryId, request.active());
        return categoryMapper.toResponse(category);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
