package com.grocery.store.controller;

import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.service.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. /internal/** is never routed by the API gateway,
 * so only other services inside the network can call it.
 */
@Tag(name = "Internal", description = "For other microservices only - not reachable through the API gateway")
@RestController
@RequestMapping("/internal/stores")
@RequiredArgsConstructor
public class InternalStoreController {

    private final StoreQueryService storeQueryService;

    @Operation(summary = "Get a store in any status (used by order-service to check status, open hours and address)")
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Store fetched successfully", storeQueryService.getStoreForService(storeId)));
    }
}
