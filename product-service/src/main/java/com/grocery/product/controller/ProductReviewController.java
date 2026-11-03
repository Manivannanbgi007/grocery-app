package com.grocery.product.controller;

import com.grocery.product.context.UserContextHolder;
import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.common.PageResponse;
import com.grocery.product.dto.request.ReviewRequest;
import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.service.ProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews of one product. Reading is public; writing needs the CUSTOMER role (checked by the API gateway).
 */
@Tag(name = "Product reviews", description = "Read reviews (public) and review a product you received (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    @Operation(summary = "Review a product of one of my delivered orders (1-5 stars, once per order)")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> writeReview(@PathVariable Long productId,
                                                                   @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = productReviewService.writeReview(UserContextHolder.getRequiredUserId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Thank you for your review", review));
    }

    @Operation(summary = "The visible reviews of a product, newest first")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> listReviews(
            @PathVariable Long productId,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews fetched successfully",
                productReviewService.listProductReviews(productId, pageable)));
    }
}
