package com.grocery.productread.dto.response;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String imageUrl,
        Integer displayOrder,
        boolean active) {
}
