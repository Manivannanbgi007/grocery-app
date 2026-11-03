package com.grocery.auth.dto.response;

import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;

import java.time.Instant;

/**
 * User details returned by the API. Never contains the password hash.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Long storeId,
        Instant lastLoginAt,
        Instant createdAt) {
}
