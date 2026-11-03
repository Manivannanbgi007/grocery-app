package com.grocery.customer.controller;

import com.grocery.customer.dto.common.ApiResponse;
import com.grocery.customer.dto.common.PageResponse;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;
import com.grocery.customer.service.AdminCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin panel: look up customers (for support).
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Customers", description = "Search customers (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    @Operation(summary = "Search customers by name, email or phone")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> searchCustomers(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<CustomerResponse> customers = adminCustomerService.searchCustomers(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Customers fetched successfully", customers));
    }

    @Operation(summary = "Get one customer")
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", adminCustomerService.getCustomer(customerId)));
    }

    @Operation(summary = "Get a customer's addresses")
    @GetMapping("/{customerId}/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getCustomerAddresses(@PathVariable Long customerId) {
        List<AddressResponse> addresses = adminCustomerService.getCustomerAddresses(customerId);
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully", addresses));
    }
}
