package com.grocery.customer.controller;

import com.grocery.customer.dto.common.ApiResponse;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.service.CustomerService;
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
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Get one address of a user (userId = auth-service user id). Used by order-service at checkout.")
    @GetMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddressOfUser(@PathVariable Long userId,
                                                                         @PathVariable Long addressId) {
        AddressResponse address = customerService.getAddressOfUser(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success("Address fetched successfully", address));
    }
}
