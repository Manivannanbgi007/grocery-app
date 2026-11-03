package com.grocery.cart.controller;

import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.dto.response.PublicCouponResponse;
import com.grocery.cart.service.CouponQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer panel: the offers page. Public - no login needed.
 */
@Tag(name = "Coupons", description = "Offers customers can use right now (public)")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class PublicCouponController {

    private final CouponQueryService couponQueryService;

    @Operation(summary = "Coupons usable right now: platform coupons, plus the coupons of one store")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicCouponResponse>>> listAvailableCoupons(
            @Parameter(description = "Also show this store's own coupons") @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Coupons fetched successfully", couponQueryService.listAvailableCoupons(storeId)));
    }
}
