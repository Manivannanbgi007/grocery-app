package com.grocery.store.controller;

import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.request.UpdateCommissionRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.service.AdminStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin panel: financial settings per store.
 * Allowed role (checked by the API gateway): SUPER_ADMIN.
 */
@Tag(name = "Super Admin - Stores", description = "Commission per store (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/super-admin/stores")
@RequiredArgsConstructor
public class SuperAdminStoreController {

    private final AdminStoreService adminStoreService;

    @Operation(summary = "Set the platform commission percentage of a store")
    @PatchMapping("/{storeId}/commission")
    public ResponseEntity<ApiResponse<StoreResponse>> updateCommission(@PathVariable Long storeId,
                                                                       @Valid @RequestBody UpdateCommissionRequest request) {
        StoreResponse store = adminStoreService.updateCommission(storeId, request);
        return ResponseEntity.ok(ApiResponse.success("Commission updated successfully", store));
    }
}
