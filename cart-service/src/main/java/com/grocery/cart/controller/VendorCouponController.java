package com.grocery.cart.controller;

import com.grocery.cart.context.UserContextHolder;
import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.request.CouponStatusRequest;
import com.grocery.cart.dto.response.CouponResponse;
import com.grocery.cart.service.VendorCouponService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store panel: coupons of MY store (store id from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - Coupons", description = "Offers and coupons for your own store (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/coupons")
@RequiredArgsConstructor
public class VendorCouponController {

    private final VendorCouponService vendorCouponService;

    @Operation(summary = "List my store's coupons")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> listMyCoupons(
            @ParameterObject @PageableDefault(size = 20, sort = "validUntil", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CouponResponse> coupons = vendorCouponService.listMyCoupons(UserContextHolder.getRequiredStoreId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Coupons fetched successfully", coupons));
    }

    @Operation(summary = "Get one of my coupons")
    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> getMyCoupon(@PathVariable Long couponId) {
        CouponResponse coupon = vendorCouponService.getMyCoupon(UserContextHolder.getRequiredStoreId(), couponId);
        return ResponseEntity.ok(ApiResponse.success("Coupon fetched successfully", coupon));
    }

    @Operation(summary = "Create a coupon for my store")
    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        CouponResponse coupon = vendorCouponService.createCoupon(
                UserContextHolder.getRequiredStoreId(), UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Coupon created successfully", coupon));
    }

    @Operation(summary = "Update one of my coupons")
    @PutMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(@PathVariable Long couponId,
                                                                    @Valid @RequestBody CouponRequest request) {
        CouponResponse coupon = vendorCouponService.updateCoupon(UserContextHolder.getRequiredStoreId(), couponId, request);
        return ResponseEntity.ok(ApiResponse.success("Coupon updated successfully", coupon));
    }

    @Operation(summary = "Switch one of my coupons on or off")
    @PatchMapping("/{couponId}/active")
    public ResponseEntity<ApiResponse<CouponResponse>> changeStatus(@PathVariable Long couponId,
                                                                    @Valid @RequestBody CouponStatusRequest request) {
        CouponResponse coupon = vendorCouponService.changeStatus(UserContextHolder.getRequiredStoreId(), couponId, request.active());
        return ResponseEntity.ok(ApiResponse.success("Coupon status updated", coupon));
    }
}
