package com.grocery.auth.controller;

import com.grocery.auth.dto.common.ApiResponse;
import com.grocery.auth.dto.common.PageResponse;
import com.grocery.auth.dto.request.UpdateUserStatusRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.service.UserAdminService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: manage customers, vendors and delivery partners.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Users", description = "Search users and enable or disable accounts (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Search users by role, status and name/email keyword")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<UserResponse> users = userAdminService.searchUsers(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", users));
    }

    @Operation(summary = "Get one user by id")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", userAdminService.getUser(userId)));
    }

    @Operation(summary = "Enable or disable a user account")
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(@PathVariable Long userId,
                                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse user = userAdminService.updateStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", user));
    }
}
