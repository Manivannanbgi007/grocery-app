package com.grocery.product.controller;

import com.grocery.product.context.UserContextHolder;
import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.dto.common.PageResponse;
import com.grocery.product.dto.response.ReviewResponse;
import com.grocery.product.enums.ReviewStatus;
import com.grocery.product.service.ProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store panel: the reviews of MY store's products (the store comes from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - Reviews", description = "Customer ratings and reviews of your products (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/reviews")
@RequiredArgsConstructor
public class VendorReviewController {

    private final ProductReviewService productReviewService;

    @Operation(summary = "Reviews of my products, e.g. rating=1 to find unhappy customers")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> listMyReviews(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reviews fetched successfully",
                productReviewService.searchReviews(UserContextHolder.getRequiredStoreId(), productId, rating, ReviewStatus.VISIBLE, pageable)));
    }
}
