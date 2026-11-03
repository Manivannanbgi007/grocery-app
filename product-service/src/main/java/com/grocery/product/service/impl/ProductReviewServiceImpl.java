package com.grocery.product.service.impl;

import com.grocery.product.client.OrderClient;
import com.grocery.product.client.dto.OrderInfo;
import com.grocery.product.dto.common.PageResponse;
import com.grocery.product.dto.request.ReviewRequest;
import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.entity.Product;
import com.grocery.product.entity.ProductReview;
import com.grocery.product.enums.ProductStatus;
import com.grocery.product.enums.ReviewStatus;
import com.grocery.product.exception.BusinessException;
import com.grocery.product.exception.DuplicateResourceException;
import com.grocery.product.exception.ResourceNotFoundException;
import com.grocery.product.mapper.ReviewMapper;
import com.grocery.product.repository.ProductRepository;
import com.grocery.product.repository.ProductReviewRepository;
import com.grocery.product.repository.RatingSummary;
import com.grocery.product.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderClient orderClient;
    private final ReviewMapper reviewMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * Not @Transactional on purpose: order-service is asked first, WITHOUT a database transaction open
     * (a slow service must not hold a database connection). Only the save runs in a transaction.
     */
    @Override
    public ReviewResponse writeReview(Long customerUserId, Long productId, ReviewRequest request) {
        findProduct(productId);
        OrderInfo order = orderClient.findOrder(request.orderId())
                .filter(found -> customerUserId.equals(found.customerUserId()))   // another customer's order looks like none
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));
        if (!order.isDelivered()) {
            throw new BusinessException("You can review the products of an order once it is delivered");
        }
        if (!order.contains(productId)) {
            throw new BusinessException("Product " + productId + " is not part of order " + request.orderId());
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        String reviewerName = ProductReview.publicName(order.deliveryAddress() == null ? null : order.deliveryAddress().contactName());

        return transactionTemplate.execute(status -> {
            if (reviewRepository.existsByOrderIdAndProductId(request.orderId(), productId)) {
                throw new DuplicateResourceException("You have already reviewed this product for order " + request.orderId());
            }
            Product product = findProduct(productId);
            ProductReview review = reviewRepository.save(
                    ProductReview.write(product, request.orderId(), customerUserId, reviewerName, request.rating(), comment));
            refreshRating(product);
            log.info("Product id={} reviewed: {} stars (now {} from {} reviews)", productId, request.rating(),
                    product.getRatingAverage(), product.getRatingCount());
            return reviewMapper.toResponse(review);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listProductReviews(Long productId, Pageable pageable) {
        findProduct(productId);
        return PageResponse.from(reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.VISIBLE, pageable)
                .map(reviewMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> searchReviews(Long storeId, Long productId, Integer rating, ReviewStatus status, Pageable pageable) {
        return PageResponse.from(reviewRepository.search(storeId, productId, rating, status, pageable).map(reviewMapper::toResponse));
    }

    @Override
    @Transactional
    public ReviewResponse hideReview(Long reviewId, String reason) {
        ProductReview review = findReview(reviewId);
        review.hide(reason.trim());
        refreshRating(findProduct(review.getProductId()));
        log.info("Review id={} hidden: {}", reviewId, reason);
        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse showReview(Long reviewId) {
        ProductReview review = findReview(reviewId);
        review.show();
        refreshRating(findProduct(review.getProductId()));
        return reviewMapper.toResponse(review);
    }

    /** Recalculates the product's rating from its visible reviews (simple and always correct). */
    private void refreshRating(Product product) {
        reviewRepository.flush();   // the new or changed review must be counted by the query
        RatingSummary summary = reviewRepository.ratingOf(product.getId());
        product.updateRating(summary.average(), summary.count());
    }

    private Product findProduct(Long productId) {
        return productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private ProductReview findReview(Long reviewId) {
        return reviewRepository.findById(reviewId).orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
    }
}
