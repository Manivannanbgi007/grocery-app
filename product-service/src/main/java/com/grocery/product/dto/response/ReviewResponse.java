package com.grocery.product.dto.response;

import com.grocery.product.enums.ReviewStatus;

import java.time.Instant;

/**
 * One review. Customers see VISIBLE reviews only; vendors and admins also see hidden ones and why.
 */
public record ReviewResponse(
        Long id,
        Long productId,
        Long storeId,
        Long orderId,
        String reviewerName,
        int rating,
        String comment,
        ReviewStatus status,
        String hiddenReason,
        Instant createdAt) {
}
