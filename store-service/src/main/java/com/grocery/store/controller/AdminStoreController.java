package com.grocery.store.controller;

import com.grocery.store.context.UserContextHolder;
import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.request.ReasonRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.service.AdminStoreService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: review and manage all stores.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Stores", description = "Approve, reject, disable and enable stores (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
public class AdminStoreController {

    private final AdminStoreService adminStoreService;

    @Operation(summary = "Search stores by status, city and name (e.g. status=PENDING_APPROVAL for the approval queue)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> searchStores(
            @RequestParam(required = false) StoreStatus status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<StoreResponse> stores = adminStoreService.searchStores(status, city, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stores fetched successfully", stores));
    }

    @Operation(summary = "Get one store")
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Store fetched successfully", adminStoreService.getStore(storeId)));
    }

    @Operation(summary = "Approve a store waiting for approval")
    @PostMapping("/{storeId}/approve")
    public ResponseEntity<ApiResponse<StoreResponse>> approveStore(@PathVariable Long storeId) {
        StoreResponse store = adminStoreService.approveStore(storeId, UserContextHolder.getRequiredUserId());
        return ResponseEntity.ok(ApiResponse.success("Store approved successfully", store));
    }

    @Operation(summary = "Reject a store waiting for approval")
    @PostMapping("/{storeId}/reject")
    public ResponseEntity<ApiResponse<StoreResponse>> rejectStore(@PathVariable Long storeId,
                                                                  @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Store rejected", adminStoreService.rejectStore(storeId, request)));
    }

    @Operation(summary = "Disable an active store (it disappears for customers)")
    @PostMapping("/{storeId}/disable")
    public ResponseEntity<ApiResponse<StoreResponse>> disableStore(@PathVariable Long storeId,
                                                                   @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Store disabled", adminStoreService.disableStore(storeId, request)));
    }

    @Operation(summary = "Enable a disabled store again")
    @PostMapping("/{storeId}/enable")
    public ResponseEntity<ApiResponse<StoreResponse>> enableStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Store enabled", adminStoreService.enableStore(storeId)));
    }
}
