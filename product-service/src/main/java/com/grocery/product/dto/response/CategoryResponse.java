package com.grocery.product.dto.response;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String imageUrl,
        int displayOrder,
        boolean active) {
}
