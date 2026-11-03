package com.grocery.auth.service;

import com.grocery.auth.dto.common.PageResponse;
import com.grocery.auth.dto.request.ChangeRoleRequest;
import com.grocery.auth.dto.request.CreateAdminRequest;
import com.grocery.auth.dto.request.UpdateUserStatusRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import org.springframework.data.domain.Pageable;

/**
 * User management for the Admin and Super Admin panels.
 */
public interface UserAdminService {

    /** Every filter is optional (null = no filter). */
    PageResponse<UserResponse> searchUsers(Role role, UserStatus status, String keyword, Pageable pageable);

    UserResponse getUser(Long userId);

    /** Enables or disables an account. A SUPER_ADMIN account cannot be changed. */
    UserResponse updateStatus(Long userId, UpdateUserStatusRequest request);

    /** Super admin only: creates a new ADMIN user. */
    UserResponse createAdmin(CreateAdminRequest request);

    /** Super admin only: changes a user's role. SUPER_ADMIN can never be assigned or changed. */
    UserResponse changeRole(Long userId, ChangeRoleRequest request);
}
