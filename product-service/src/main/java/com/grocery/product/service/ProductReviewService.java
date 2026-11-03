package com.grocery.product.service;

import com.grocery.product.dto.common.PageResponse;
import com.grocery.product.dto.request.ReviewRequest;
import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;

/**
 * Product reviews: customers write them, everybody reads them, vendors see their store's, admins moderate.
 * Every change updates the product's ratingAverage / ratingCount (Debezium then copies them to search).
 */
public interface ProductReviewService {

    /** Only for a product the customer received: the order must be theirs, DELIVERED and contain the product. */
    ReviewResponse writeReview(Long customerUserId, Long productId, ReviewRequest request);

    /** Public: the visible reviews of a product. */
    PageResponse<ReviewResponse> listProductReviews(Long productId, Pageable pageable);

    /** storeId = null for admins (all stores); every other filter is optional too. */
    PageResponse<ReviewResponse> searchReviews(Long storeId, Long productId, Integer rating, ReviewStatus status, Pageable pageable);

    ReviewResponse hideReview(Long reviewId, String reason);

    ReviewResponse showReview(Long reviewId);
}
