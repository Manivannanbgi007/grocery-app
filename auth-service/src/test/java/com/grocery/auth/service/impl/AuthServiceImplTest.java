package com.grocery.auth.service.impl;

import com.grocery.auth.config.PasswordResetProperties;
import com.grocery.auth.dto.request.ChangePasswordRequest;
import com.grocery.auth.dto.request.ForgotPasswordRequest;
import com.grocery.auth.dto.request.LoginRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.request.ResetPasswordRequest;
import com.grocery.auth.dto.response.AuthResponse;
import com.grocery.auth.entity.PasswordResetToken;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.exception.BusinessException;
import com.grocery.auth.exception.DuplicateResourceException;
import com.grocery.auth.exception.ForbiddenException;
import com.grocery.auth.exception.UnauthorizedException;
import com.grocery.auth.mapper.UserEventMapper;
import com.grocery.auth.mapper.UserMapper;
import com.grocery.auth.messaging.OutboxEventPublisher;
import com.grocery.auth.repository.PasswordResetTokenRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.security.ResetTokenCipher;
import com.grocery.auth.security.Sha256;
import com.grocery.auth.service.JwtService;
import com.grocery.auth.service.LoginSessions;
import com.grocery.auth.service.SessionService;
import com.grocery.auth.validator.UserValidator;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.UserRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthServiceImpl: the repositories and JwtService are mocked,
 * the mapper, validator and password encoder are real.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PASSWORD = "Password@123";
    /** Base64 of 32 bytes, like PASSWORD_RESET_EVENT_KEY */
    private static final String EVENT_KEY = "bG9jYWwtZGV2LW9ubHktY2hhbmdlLW1lLTAxMjM0NTY=";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private SessionService sessionService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);   // low strength = fast tests

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        PasswordResetProperties passwordResetProperties = new PasswordResetProperties(30, true, EVENT_KEY);
        authService = new AuthServiceImpl(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                new LoginSessions(sessionService, jwtService, new UserMapper()),
                new UserMapper(),
                new UserValidator(userRepository),
                passwordResetProperties,
                outboxEventPublisher,
                new UserEventMapper(),
                sessionService,
                new ResetTokenCipher(passwordResetProperties));
    }

    // ----- register -----

    @Test
    void register_savesActiveUserWithHashedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("Neha Gupta", "Neha.Gupta@Example.com", "9876543210", PASSWORD, Role.CUSTOMER);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return user;
        });
        stubJwtService();

        AuthResponse response = authService.register(request, null).response();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("neha.gupta@example.com");
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches(PASSWORD, savedUser.getValue().getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("test-token");
        assertThat(response.user().id()).isEqualTo(11L);

        ArgumentCaptor<UserRegistered> event = ArgumentCaptor.forClass(UserRegistered.class);
        verify(outboxEventPublisher).publish(eq("user"), eq(11L), event.capture());
        assertThat(event.getValue().getEmail()).isEqualTo("neha.gupta@example.com");
        assertThat(event.getValue().getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void register_rejectsRolesThatCannotSelfRegister() {
        RegisterRequest request = new RegisterRequest("Eve", "eve@example.com", "9876543210", PASSWORD, Role.ADMIN);

        assertThatThrownBy(() -> authService.register(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsAlreadyRegisteredEmail() {
        RegisterRequest request = new RegisterRequest("Priya", "priya@example.com", "9876543210", PASSWORD, Role.CUSTOMER);
        when(userRepository.existsByEmail("priya@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("priya@example.com");
        verify(userRepository, never()).save(any());
    }

    // ----- login -----

    @Test
    void login_returnsTokenAndRecordsLoginTime_forCorrectCredentials() {
        User user = user(4L, "customer1@grocery.com", UserStatus.ACTIVE);
        when(userRepository.findByEmail("customer1@grocery.com")).thenReturn(Optional.of(user));
        stubJwtService();

        AuthResponse response = authService.login(new LoginRequest("customer1@grocery.com", PASSWORD), null).response();

        assertThat(response.accessToken()).isEqualTo("test-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_fails_withWrongPassword() {
        when(userRepository.findByEmail("customer1@grocery.com"))
                .thenReturn(Optional.of(user(4L, "customer1@grocery.com", UserStatus.ACTIVE)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer1@grocery.com", "WrongPassword1"), null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_fails_forUnknownEmail_withSameMessageAsWrongPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD), null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_fails_forDisabledAccount() {
        when(userRepository.findByEmail("customer3@grocery.com"))
                .thenReturn(Optional.of(user(9L, "customer3@grocery.com", UserStatus.DISABLED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer3@grocery.com", PASSWORD), null))
                .isInstanceOf(ForbiddenException.class);
    }

    // ----- passwords -----

    @Test
    void changePassword_setsTheNewPassword_andEndsOpenResetTokens() {
        User user = user(4L, "customer1@grocery.com", UserStatus.ACTIVE);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        authService.changePassword(4L, new ChangePasswordRequest(PASSWORD, "NewPassword@456"), null);

        assertThat(passwordEncoder.matches("NewPassword@456", user.getPasswordHash())).isTrue();
        // A reset email asked for before the change must not undo it
        verify(passwordResetTokenRepository).invalidateAllOfUser(4L);
    }

    @Test
    void changePassword_fails_whenCurrentPasswordIsWrong() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(user(4L, "customer1@grocery.com", UserStatus.ACTIVE)));

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword1", "NewPassword@456");

        assertThatThrownBy(() -> authService.changePassword(4L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Current password is incorrect");
    }

    @Test
    void forgotPassword_returnsNothing_forUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(authService.forgotPassword(new ForgotPasswordRequest("nobody@example.com"))).isNull();
        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void forgotPassword_storesOnlyTheHash_andPublishesTheTokenEncrypted() {
        when(userRepository.findByEmail("customer1@grocery.com"))
                .thenReturn(Optional.of(user(4L, "customer1@grocery.com", UserStatus.ACTIVE)));

        String token = authService.forgotPassword(new ForgotPasswordRequest("customer1@grocery.com")).resetToken();

        // Earlier tokens stop working; the database keeps only the hash of the new one
        verify(passwordResetTokenRepository).invalidateAllOfUser(4L);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(saved.capture());
        assertThat(token).hasSize(32);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(Sha256.hex(token)).isNotEqualTo(token);

        // The event carries the token encrypted, never in plain text
        ArgumentCaptor<PasswordResetRequested> event = ArgumentCaptor.forClass(PasswordResetRequested.class);
        verify(outboxEventPublisher).publish(eq("user"), eq(4L), event.capture());
        assertThat(PasswordResetRequested.getClassSchema().getField("resetToken")).isNull();   // no plain field at all
        assertThat(event.getValue().getEncryptedResetToken()).startsWith("v1.").doesNotContain(token);
    }

    @Test
    void resetPassword_findsTheTokenByItsHash_setsNewPassword_andEndsTheOtherTokens() {
        User user = user(4L, "customer1@grocery.com", UserStatus.ACTIVE);
        PasswordResetToken token = resetToken(user, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(passwordResetTokenRepository.findByTokenHash(Sha256.hex("valid-token"))).thenReturn(Optional.of(token));

        authService.resetPassword(new ResetPasswordRequest(" valid-token ", "NewPassword@456"));   // pasted with spaces

        assertThat(passwordEncoder.matches("NewPassword@456", user.getPasswordHash())).isTrue();
        assertThat(token.isUsed()).isTrue();
        verify(passwordResetTokenRepository).invalidateAllOfUser(4L);
    }

    @Test
    void resetPassword_fails_forExpiredToken() {
        User user = user(4L, "customer1@grocery.com", UserStatus.ACTIVE);
        PasswordResetToken expired = resetToken(user, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(passwordResetTokenRepository.findByTokenHash(Sha256.hex("old-token"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("old-token", "NewPassword@456")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Reset token is invalid or has expired");
    }

    // ----- helpers -----

    private void stubJwtService() {
        when(jwtService.generateToken(any(User.class))).thenReturn("test-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
    }

    private User user(Long id, String email, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPhone("9000000004");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
    }

    private PasswordResetToken resetToken(User user, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(expiresAt);
        return token;
    }
}
