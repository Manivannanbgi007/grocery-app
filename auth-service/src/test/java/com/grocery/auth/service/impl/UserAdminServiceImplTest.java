package com.grocery.auth.service.impl;

import com.grocery.auth.dto.request.ChangeRoleRequest;
import com.grocery.auth.dto.request.CreateAdminRequest;
import com.grocery.auth.dto.request.UpdateUserStatusRequest;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.exception.BusinessException;
import com.grocery.auth.exception.ResourceNotFoundException;
import com.grocery.auth.mapper.UserMapper;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.service.SessionService;
import com.grocery.auth.validator.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionService sessionService;

    private UserAdminServiceImpl userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminServiceImpl(
                userRepository, new UserMapper(), new UserValidator(userRepository), new BCryptPasswordEncoder(4), sessionService);
    }

    @Test
    void updateStatus_disablesCustomer() {
        User customer = user(4L, Role.CUSTOMER, null);
        when(userRepository.findById(4L)).thenReturn(Optional.of(customer));

        UserResponse response = userAdminService.updateStatus(4L, new UpdateUserStatusRequest(UserStatus.DISABLED));

        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void updateStatus_isNotAllowed_forSuperAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.SUPER_ADMIN, null)));

        assertThatThrownBy(() -> userAdminService.updateStatus(1L, new UpdateUserStatusRequest(UserStatus.DISABLED)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getUser_fails_whenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.getUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id: 99");
    }

    @Test
    void createAdmin_savesActiveAdmin() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse admin = userAdminService.createAdmin(
                new CreateAdminRequest("Ops Admin", "Ops.Admin@Grocery.com", "9123456780", "Password@123"));

        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.email()).isEqualTo("ops.admin@grocery.com");
    }

    @Test
    void changeRole_cannotAssignSuperAdmin() {
        assertThatThrownBy(() -> userAdminService.changeRole(4L, new ChangeRoleRequest(Role.SUPER_ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The SUPER_ADMIN role cannot be assigned");
    }

    @Test
    void changeRole_clearsStoreId_whenVendorBecomesCustomer() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, Role.VENDOR, 1L)));

        UserResponse response = userAdminService.changeRole(3L, new ChangeRoleRequest(Role.CUSTOMER));

        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.storeId()).isNull();
    }

    private User user(Long id, Role role, Long storeId) {
        User user = new User();
        user.setId(id);
        user.setFullName("Test User");
        user.setEmail("user" + id + "@grocery.com");
        user.setPhone("90000000" + String.format("%02d", id));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setStoreId(storeId);
        return user;
    }
}
