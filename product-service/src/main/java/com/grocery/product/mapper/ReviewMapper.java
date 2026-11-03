package com.grocery.product.mapper;

import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.entity.ProductReview;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(ProductReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getStoreId(),
                review.getOrderId(),
                review.getReviewerName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getHiddenReason(),
                review.getCreatedAt());
    }
}
