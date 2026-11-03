package com.grocery.product.repository;

/**
 * @param average null when the product has no visible reviews
 */
public record RatingSummary(Long count, Double average) {
}
