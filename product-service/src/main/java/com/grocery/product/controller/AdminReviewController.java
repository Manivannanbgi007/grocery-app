package com.grocery.product.controller;

import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.common.PageResponse;
import com.grocery.product.dto.request.HideReviewRequest;
import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.enums.ReviewStatus;
import com.grocery.product.service.ProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: moderate reviews. A hidden review no longer counts for the product's rating.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Reviews", description = "Moderate product reviews (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ProductReviewService productReviewService;

    @Operation(summary = "All reviews; every filter is optional")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> searchReviews(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) ReviewStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews fetched successfully",
                productReviewService.searchReviews(storeId, productId, rating, status, pageable)));
    }

    @Operation(summary = "Hide a review (offensive, fake, not about the product)")
    @PatchMapping("/{reviewId}/hide")
    public ResponseEntity<ApiResponse<ReviewResponse>> hideReview(@PathVariable Long reviewId, @Valid @RequestBody HideReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review hidden", productReviewService.hideReview(reviewId, request.reason())));
    }

    @Operation(summary = "Show a hidden review again")
    @PatchMapping("/{reviewId}/show")
    public ResponseEntity<ApiResponse<ReviewResponse>> showReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(ApiResponse.success("Review visible again", productReviewService.showReview(reviewId)));
    }
}
