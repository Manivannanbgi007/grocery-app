package com.grocery.store.controller;

import com.grocery.store.context.UserContextHolder;
import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.request.StoreDetailsRequest;
import com.grocery.store.dto.request.UpdateAcceptingOrdersRequest;
import com.grocery.store.dto.request.UpdateDeliveryAreasRequest;
import com.grocery.store.dto.request.UpdateOpeningHoursRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.service.VendorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store panel: the logged-in vendor manages THEIR OWN store.
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - My Store", description = "Register and manage your own store (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/store")
@RequiredArgsConstructor
public class VendorStoreController {

    private final VendorStoreService vendorStoreService;

    @Operation(summary = "Register my store (it then waits for admin approval)")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> registerStore(@Valid @RequestBody StoreDetailsRequest request) {
        StoreResponse store = vendorStoreService.registerStore(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Store registered. It is now waiting for admin approval.", store));
    }

    @Operation(summary = "Get my store")
    @GetMapping
    public ResponseEntity<ApiResponse<StoreResponse>> getMyStore() {
        StoreResponse store = vendorStoreService.getMyStore(UserContextHolder.getRequiredUserId());
        return ResponseEntity.ok(ApiResponse.success("Store fetched successfully", store));
    }

    @Operation(summary = "Update my store details (a rejected store goes back for approval)")
    @PutMapping
    public ResponseEntity<ApiResponse<StoreResponse>> updateMyStore(@Valid @RequestBody StoreDetailsRequest request) {
        StoreResponse store = vendorStoreService.updateMyStore(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Store updated successfully", store));
    }

    @Operation(summary = "Set the weekly opening hours (days not listed are closed)")
    @PutMapping("/opening-hours")
    public ResponseEntity<ApiResponse<StoreResponse>> updateOpeningHours(@Valid @RequestBody UpdateOpeningHoursRequest request) {
        StoreResponse store = vendorStoreService.updateOpeningHours(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Opening hours updated successfully", store));
    }

    @Operation(summary = "Set the PIN codes my store delivers to")
    @PutMapping("/delivery-areas")
    public ResponseEntity<ApiResponse<StoreResponse>> updateDeliveryAreas(@Valid @RequestBody UpdateDeliveryAreasRequest request) {
        StoreResponse store = vendorStoreService.updateDeliveryAreas(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Delivery areas updated successfully", store));
    }

    @Operation(summary = "Pause or resume new orders")
    @PatchMapping("/accepting-orders")
    public ResponseEntity<ApiResponse<StoreResponse>> updateAcceptingOrders(@Valid @RequestBody UpdateAcceptingOrdersRequest request) {
        StoreResponse store = vendorStoreService.updateAcceptingOrders(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Store order intake updated successfully", store));
    }
}
