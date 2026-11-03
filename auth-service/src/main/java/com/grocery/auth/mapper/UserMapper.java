package com.grocery.auth.mapper;

import com.grocery.auth.dto.request.CreateAdminRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.entity.User;
import org.springframework.stereotype.Component;

/**
 * Converts between User entities and DTOs.
 * Only copies fields - business decisions (status, password hashing) stay in the service.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getStoreId(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setPhone(request.phone());
        user.setRole(request.role());
        return user;
    }

    public User toEntity(CreateAdminRequest request) {
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setPhone(request.phone());
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
