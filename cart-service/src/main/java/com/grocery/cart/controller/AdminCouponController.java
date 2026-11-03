package com.grocery.cart.controller;

import com.grocery.cart.context.UserContextHolder;
import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.request.CouponStatusRequest;
import com.grocery.cart.dto.response.CouponResponse;
import com.grocery.cart.service.AdminCouponService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: platform-wide coupons, and moderation of store coupons.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Coupons", description = "Platform coupons and moderation of store coupons (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @Operation(summary = "Search coupons: all, one store's (storeId) or platform coupons only (platformOnly=true)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> searchCoupons(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "false") boolean platformOnly,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CouponResponse> coupons = adminCouponService.searchCoupons(storeId, platformOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Coupons fetched successfully", coupons));
    }

    @Operation(summary = "Get any coupon")
    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable Long couponId) {
        return ResponseEntity.ok(ApiResponse.success("Coupon fetched successfully", adminCouponService.getCoupon(couponId)));
    }

    @Operation(summary = "Create a platform coupon (valid in every store)")
    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createPlatformCoupon(@Valid @RequestBody CouponRequest request) {
        CouponResponse coupon = adminCouponService.createPlatformCoupon(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Coupon created successfully", coupon));
    }

    @Operation(summary = "Update a platform coupon")
    @PutMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> updatePlatformCoupon(@PathVariable Long couponId,
                                                                            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Coupon updated successfully", adminCouponService.updatePlatformCoupon(couponId, request)));
    }

    @Operation(summary = "Switch any coupon on or off")
    @PatchMapping("/{couponId}/active")
    public ResponseEntity<ApiResponse<CouponResponse>> changeStatus(@PathVariable Long couponId,
                                                                    @Valid @RequestBody CouponStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Coupon status updated", adminCouponService.changeStatus(couponId, request.active())));
    }
}
