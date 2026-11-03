package com.grocery.store.controller;

import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.response.PublicStoreResponse;
import com.grocery.store.service.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel: browse stores. Public - no login needed (GET only, see the gateway's SecurityConfig).
 */
@Tag(name = "Stores", description = "Browse active stores (public)")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class PublicStoreController {

    private final StoreQueryService storeQueryService;

    @Operation(summary = "List active stores, optionally in a city or delivering to a PIN code")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PublicStoreResponse>>> listStores(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pincode,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        PageResponse<PublicStoreResponse> stores = storeQueryService.listActiveStores(city, pincode, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stores fetched successfully", stores));
    }

    @Operation(summary = "Get one active store")
    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<PublicStoreResponse>> getStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Store fetched successfully", storeQueryService.getActiveStore(storeId)));
    }
}
