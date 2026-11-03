package com.grocery.product.entity;

import com.grocery.product.enums.ReviewStatus;
import com.grocery.product.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer's review of a product they received: 1 to 5 stars and an optional comment.
 * One review per product per order (unique key in the table).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use ProductReview.write(...)
@Entity
@Table(name = "product_reviews")
public class ProductReview extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    @Column(name = "reviewer_name", nullable = false, length = 60)
    private String reviewerName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewStatus status;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    public static ProductReview write(Product product, Long orderId, Long customerUserId, String reviewerName,
                                      int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("The rating must be 1 to 5 stars");
        }
        ProductReview review = new ProductReview();
        review.productId = product.getId();
        review.storeId = product.getStoreId();
        review.orderId = orderId;
        review.customerUserId = customerUserId;
        review.reviewerName = reviewerName;
        review.rating = rating;
        review.comment = comment;
        review.status = ReviewStatus.VISIBLE;
        return review;
    }

    public void hide(String reason) {
        this.status = ReviewStatus.HIDDEN;
        this.hiddenReason = reason;
    }

    public void show() {
        this.status = ReviewStatus.VISIBLE;
        this.hiddenReason = null;
    }

    /** "Priya Sharma" -> "Priya S." (only the first name and an initial are shown publicly). */
    public static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
