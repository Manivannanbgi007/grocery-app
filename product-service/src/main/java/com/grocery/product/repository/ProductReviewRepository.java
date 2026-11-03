package com.grocery.product.repository;

import com.grocery.product.entity.ProductReview;
import com.grocery.product.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    boolean existsByOrderIdAndProductId(Long orderId, Long productId);

    Page<ProductReview> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    /** Vendor and admin lists: every filter is optional (null = ignore it). */
    @Query("""
            SELECT r FROM ProductReview r
            WHERE (:storeId IS NULL OR r.storeId = :storeId)
              AND (:productId IS NULL OR r.productId = :productId)
              AND (:rating IS NULL OR r.rating = :rating)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<ProductReview> search(Long storeId, Long productId, Integer rating, ReviewStatus status, Pageable pageable);

    /** Number and average stars of a product's visible reviews. */
    @Query("""
            SELECT new com.grocery.product.repository.RatingSummary(COUNT(r), AVG(r.rating))
            FROM ProductReview r
            WHERE r.productId = :productId AND r.status = com.grocery.product.enums.ReviewStatus.VISIBLE
            """)
    RatingSummary ratingOf(Long productId);
}
