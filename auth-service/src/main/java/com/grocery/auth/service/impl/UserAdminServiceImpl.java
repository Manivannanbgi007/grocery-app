package com.grocery.auth.service.impl;

import com.grocery.auth.dto.common.PageResponse;
import com.grocery.auth.dto.request.ChangeRoleRequest;
import com.grocery.auth.dto.request.CreateAdminRequest;
import com.grocery.auth.dto.request.UpdateUserStatusRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.exception.BusinessException;
import com.grocery.auth.exception.ResourceNotFoundException;
import com.grocery.auth.mapper.UserMapper;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.service.SessionService;
import com.grocery.auth.service.UserAdminService;
import com.grocery.auth.validator.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(Role role, UserStatus status, String keyword, Pageable pageable) {
        String keywordFilter = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.from(
                userRepository.search(role, status, keywordFilter, pageable).map(userMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long userId, UpdateUserStatusRequest request) {
        User user = findUser(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("The status of a SUPER_ADMIN account cannot be changed");
        }

        user.setStatus(request.status());
        if (request.status() == UserStatus.DISABLED) {
            // Logged out on every device: no new access token from now on (the current one ends within 15 min)
            sessionService.endAll(userId, SessionEndReason.USER_DISABLED);
        }
        log.info("User id={} status changed to {}", userId, request.status());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createAdmin(CreateAdminRequest request) {
        userValidator.validateEmailAndPhoneAreAvailable(request.email(), request.phone());

        User admin = userMapper.toEntity(request);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        User savedAdmin = userRepository.save(admin);

        log.info("Created admin user id={}", savedAdmin.getId());
        return userMapper.toResponse(savedAdmin);
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long userId, ChangeRoleRequest request) {
        if (request.role() == Role.SUPER_ADMIN) {
            throw new BusinessException("The SUPER_ADMIN role cannot be assigned");
        }
        User user = findUser(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("The role of a SUPER_ADMIN account cannot be changed");
        }

        user.setRole(request.role());
        if (request.role() != Role.VENDOR) {
            user.setStoreId(null);   // only vendors own a store
        }
        sessionService.endAll(userId, SessionEndReason.ROLE_CHANGED);   // log in again with the new role's rights
        log.info("User id={} role changed to {}", userId, request.role());
        return userMapper.toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
