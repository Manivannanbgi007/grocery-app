package com.grocery.productread.service.impl;

import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.dto.response.CategoryResponse;
import com.grocery.productread.exception.ResourceNotFoundException;
import com.grocery.productread.mapper.ProductDocumentMapper;
import com.grocery.productread.repository.CategoryDocumentRepository;
import com.grocery.productread.service.CategoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class CategoryQueryServiceImpl implements CategoryQueryService {

    /** Home-page order, then alphabetical. */
    private static final Sort DISPLAY_ORDER = Sort.by("displayOrder").and(Sort.by("name"));

    private final CategoryDocumentRepository categoryRepository;
    private final ProductDocumentMapper mapper;

    @Override
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrue(DISPLAY_ORDER).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return StreamSupport.stream(categoryRepository.findAll(DISPLAY_ORDER).spliterator(), false)
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getActiveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(CategoryDocument::isActive)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }
}
