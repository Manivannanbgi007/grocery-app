package com.grocery.customer.controller;

import com.grocery.customer.context.UserContextHolder;
import com.grocery.customer.dto.common.ApiResponse;
import com.grocery.customer.dto.request.UpdateProfileRequest;
import com.grocery.customer.dto.response.CustomerResponse;
import com.grocery.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel: my profile.
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Customer - Profile", description = "View and update your profile (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerService customerService;

    @Operation(summary = "Get my profile")
    @GetMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> getMyProfile() {
        CustomerResponse profile = customerService.getMyProfile(UserContextHolder.getRequiredUserId());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", profile));
    }

    @Operation(summary = "Update my profile")
    @PutMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        CustomerResponse profile = customerService.updateMyProfile(UserContextHolder.getRequiredUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }
}
