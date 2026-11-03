package com.grocery.customer.controller;

import com.grocery.customer.context.UserContextHolder;
import com.grocery.customer.dto.common.ApiResponse;
import com.grocery.customer.dto.request.AddressRequest;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer panel: my delivery addresses.
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Customer - Addresses", description = "Manage your delivery addresses (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@RequiredArgsConstructor
public class CustomerAddressController {

    private final CustomerService customerService;

    @Operation(summary = "List my addresses")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses() {
        List<AddressResponse> addresses = customerService.getMyAddresses(UserContextHolder.getRequiredUserId());
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully", addresses));
    }

    @Operation(summary = "Add an address (the first one becomes the default)")
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@Valid @RequestBody AddressRequest request) {
        AddressResponse address = customerService.addAddress(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Address added successfully", address));
    }

    @Operation(summary = "Update one of my addresses")
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@PathVariable Long addressId,
                                                                      @Valid @RequestBody AddressRequest request) {
        AddressResponse address = customerService.updateAddress(UserContextHolder.getRequiredUserId(), addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", address));
    }

    @Operation(summary = "Delete one of my addresses")
    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable Long addressId) {
        customerService.deleteAddress(UserContextHolder.getRequiredUserId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully"));
    }

    @Operation(summary = "Make an address my default delivery address")
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(@PathVariable Long addressId) {
        AddressResponse address = customerService.setDefaultAddress(UserContextHolder.getRequiredUserId(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated", address));
    }
}
