package com.grocery.auth.service.impl;

import com.grocery.auth.config.PasswordResetProperties;
import com.grocery.auth.constant.KafkaTopics;
import com.grocery.auth.dto.request.ChangePasswordRequest;
import com.grocery.auth.dto.request.ForgotPasswordRequest;
import com.grocery.auth.dto.request.LoginRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.request.ResetPasswordRequest;
import com.grocery.auth.dto.response.ForgotPasswordResponse;
import com.grocery.auth.dto.response.UserResponse;
import com.grocery.auth.entity.PasswordResetToken;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.exception.BusinessException;
import com.grocery.auth.exception.ForbiddenException;
import com.grocery.auth.exception.ResourceNotFoundException;
import com.grocery.auth.exception.SessionEndedException;
import com.grocery.auth.exception.UnauthorizedException;
import com.grocery.auth.mapper.UserEventMapper;
import com.grocery.auth.mapper.UserMapper;
import com.grocery.auth.messaging.OutboxEventPublisher;
import com.grocery.auth.repository.PasswordResetTokenRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.security.ResetTokenCipher;
import com.grocery.auth.security.Sha256;
import com.grocery.auth.service.AuthService;
import com.grocery.auth.service.AuthTokens;
import com.grocery.auth.service.LoginSessions;
import com.grocery.auth.service.SessionService;
import com.grocery.auth.validator.UserValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessions loginSessions;
    private final UserMapper userMapper;
    private final UserValidator userValidator;
    private final PasswordResetProperties passwordResetProperties;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserEventMapper userEventMapper;
    private final SessionService sessionService;
    private final ResetTokenCipher resetTokenCipher;

    @Override
    @Transactional
    public AuthTokens register(RegisterRequest request, String currentRefreshToken) {
        if (!request.role().isSelfRegistrationAllowed()) {
            throw new BusinessException("Role " + request.role() + " cannot be chosen at registration");
        }
        userValidator.validateEmailAndPhoneAreAvailable(request.email(), request.phone());

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        // Same transaction as the new user: customer-service creates the customer profile from this event
        outboxEventPublisher.publish(KafkaTopics.USER_AGGREGATE, savedUser.getId(), userEventMapper.toUserRegistered(savedUser));

        log.info("Registered user id={} role={}", savedUser.getId(), savedUser.getRole());
        return loginSessions.start(savedUser, currentRefreshToken);
    }

    @Override
    @Transactional
    public AuthTokens login(LoginRequest request, String currentRefreshToken) {
        // Same message for "unknown email" and "wrong password", so nobody can guess which emails exist.
        User user = userRepository.findByEmail(request.email().trim())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new ForbiddenException("Your account is disabled. Please contact support.");
        }

        user.recordLogin(Instant.now());
        log.info("User id={} logged in", user.getId());
        return loginSessions.start(user, currentRefreshToken);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = SessionEndedException.class)
    public AuthTokens refresh(String refreshToken) {
        SessionService.Refreshed refreshed = sessionService.refresh(refreshToken);
        // Built from the CURRENT user row: a changed role or a new store is in the new access token
        return new AuthTokens(loginSessions.authResponse(refreshed.user()), refreshed.newToken());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void logout(String refreshToken) {
        sessionService.end(refreshToken, SessionEndReason.LOGOUT);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String currentRefreshToken) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Anyone who knew the old password and logged in elsewhere is logged out; this browser stays logged in
        sessionService.endAllExcept(userId, currentRefreshToken, SessionEndReason.PASSWORD_CHANGED);
        // A reset email asked for before the change must not undo it
        passwordResetTokenRepository.invalidateAllOfUser(userId);
        log.info("User id={} changed their password", userId);
    }

    @Override
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<User> user = userRepository.findByEmail(request.email().trim());
        if (user.isEmpty()) {
            // Answer exactly like the success case, so nobody can find out which emails are registered.
            log.debug("Password reset requested for unknown email");
            return null;
        }

        // 32 hex characters (122 random bits from SecureRandom): impossible to guess, easy to copy from the email
        String token = UUID.randomUUID().toString().replace("-", "");
        // Only the newest token works: the tokens of earlier emails stop working
        passwordResetTokenRepository.invalidateAllOfUser(user.get().getId());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user.get());
        resetToken.setTokenHash(Sha256.hex(token));   // the database keeps only the hash
        resetToken.setExpiresAt(Instant.now().plus(passwordResetProperties.tokenValidityMinutes(), ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);
        log.info("Password reset token created for user id={}", user.get().getId());

        // notification-service (Phase 8) emails the token from this event; in the event it is encrypted
        String encryptedToken = resetTokenCipher.encrypt(token, user.get().getId());
        outboxEventPublisher.publish(KafkaTopics.USER_AGGREGATE, user.get().getId(),
                userEventMapper.toPasswordResetRequested(resetToken, encryptedToken));

        if (passwordResetProperties.exposeTokenInResponse()) {
            return new ForgotPasswordResponse(token, resetToken.getExpiresAt());
        }
        return null;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(Sha256.hex(request.token().trim()))
                .filter(token -> token.isUsable(Instant.now()))
                .orElseThrow(() -> new BusinessException("Reset token is invalid or has expired"));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.markEmailVerified(Instant.now());   // the code was sent to this address: it is the account holder's
        // @Version: of two resets with the same token at the same moment, only one succeeds (the other gets 409)
        resetToken.markUsed();
        passwordResetTokenRepository.invalidateAllOfUser(user.getId());          // and no other email's token works
        sessionService.endAll(user.getId(), SessionEndReason.PASSWORD_RESET);   // every device must log in again
        log.info("User id={} reset their password", user.getId());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

}
