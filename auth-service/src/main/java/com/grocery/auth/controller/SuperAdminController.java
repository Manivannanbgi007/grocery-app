package com.grocery.auth.controller;

import com.grocery.auth.dto.common.ApiResponse;
import com.grocery.auth.dto.request.ChangeRoleRequest;
import com.grocery.auth.dto.request.CreateAdminRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin panel: create admins and assign roles.
 * Allowed role (checked by the API gateway): SUPER_ADMIN.
 */
@Tag(name = "Super Admin - Users", description = "Create admins and assign roles (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Create a new platform admin")
    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<UserResponse>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        UserResponse admin = userAdminService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin created successfully", admin));
    }

    @Operation(summary = "Change a user's role")
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(@PathVariable Long userId,
                                                                @Valid @RequestBody ChangeRoleRequest request) {
        UserResponse user = userAdminService.changeRole(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", user));
    }
}
