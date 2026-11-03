package com.grocery.auth.service.impl;

import com.grocery.auth.constant.KafkaTopics;
import com.grocery.auth.dto.request.LoginRequest;
import com.grocery.auth.dto.request.RegisterRequest;
import com.grocery.auth.dto.request.SocialLinkRequest;
import com.grocery.auth.dto.request.SocialSignupRequest;
import com.grocery.auth.entity.SocialLogin;
import com.grocery.auth.entity.SocialLoginFlow;
import com.grocery.auth.entity.User;
import com.grocery.auth.exception.BusinessException;
import com.grocery.auth.exception.DuplicateResourceException;
import com.grocery.auth.exception.ForbiddenException;
import com.grocery.auth.mapper.UserEventMapper;
import com.grocery.auth.messaging.OutboxEventPublisher;
import com.grocery.auth.repository.SocialLoginFlowRepository;
import com.grocery.auth.repository.SocialLoginRepository;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.security.RefreshTokens;
import com.grocery.auth.security.Sha256;
import com.grocery.auth.service.AuthService;
import com.grocery.auth.service.AuthTokens;
import com.grocery.auth.service.LoginSessions;
import com.grocery.auth.service.SocialLoginService;
import com.grocery.auth.social.SocialClientRegistrations;
import com.grocery.auth.social.SocialLoginProperties;
import com.grocery.auth.social.SocialProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The account rules of social login (docs/social-login.md, "Which account?"):
 *   1. the provider account is linked already           -> log in
 *   2. an account has the same email                    -> link automatically when BOTH sides verified the email,
 *                                                          otherwise its password is asked once (POST /social/link)
 *   3. no account                                       -> sign-up form (POST /social/signup)
 * Never for admin or super admin accounts (password only) or disabled ones. Logging in, signing up and the session are
 * the ones of the password login: AuthService.register / login and LoginSessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginServiceImpl implements SocialLoginService {

    private final SocialLoginFlowRepository flows;
    private final SocialLoginRepository socialLogins;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final LoginSessions loginSessions;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserEventMapper userEventMapper;
    private final SocialClientRegistrations registrations;
    private final SocialLoginProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public String acceptProfile(Long flowId, SocialProfile profile) {
        SocialLoginFlow flow = flows.findById(flowId)
                .filter(found -> found.getStage() == SocialLoginFlow.Stage.CALLBACK)
                .orElseThrow(() -> new IllegalStateException("No social login attempt " + flowId + " waits for its answer"));
        String code = RefreshTokens.newToken();
        flow.authenticated(profile, Sha256.hex(code), clock.instant().plus(properties.codeLifetime()));
        return code;
    }

    @Override
    @Transactional
    public SocialLoginResult redeem(String code, String browserToken, String currentRefreshToken) {
        Instant now = clock.instant();
        // Once, before it expires, and only in the browser that started the attempt
        if (isBlank(code) || isBlank(browserToken)
                || flows.redeem(Sha256.hex(code), Sha256.hex(browserToken), now, now.plus(properties.signupLifetime())) != 1) {
            throw expired();
        }
        SocialLoginFlow flow = flows.findByCodeHash(Sha256.hex(code)).orElseThrow(SocialLoginServiceImpl::expired);
        SocialProfile profile = flow.profile();

        // 1. This provider account is linked already
        Optional<SocialLogin> linked = socialLogins.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            linked.get().used(now);
            return SocialLoginResult.loggedIn(profile.provider(), logIn(linked.get().getUser(), flow, currentRefreshToken));
        }

        // 3. No account with this email (or no email at all): sign up
        Optional<User> sameEmail = profile.email() == null ? Optional.empty() : userRepository.findByEmail(profile.email());
        if (sameEmail.isEmpty()) {
            return SocialLoginResult.signupRequired(profile);
        }

        // 2. An account with the same email
        User user = sameEmail.get();
        checkMayUseSocialLogin(user);
        checkNoOtherAccountOfProvider(user, profile.provider());
        if (profile.emailVerified() && user.isEmailVerified()) {
            addLink(user, profile, true);
            return SocialLoginResult.loggedIn(profile.provider(), logIn(user, flow, currentRefreshToken));
        }
        return SocialLoginResult.passwordRequired(profile.provider(), user.getEmail());
    }

    @Override
    @Transactional
    public AuthTokens signUp(String browserToken, SocialSignupRequest request, String currentRefreshToken) {
        SocialLoginFlow flow = waitingFlow(browserToken);
        SocialProfile profile = flow.profile();
        // A verified email from the provider is used as it is; otherwise the one typed into the form
        String email = profile.emailVerified() ? profile.email() : request.email();
        if (isBlank(email)) {
            throw new BusinessException("Please enter your email address");
        }

        // The registration with a password (rules, the UserRegistered event, the session), with a random password
        // nobody knows: logging in with a password fails until "Forgot password" sets one
        RegisterRequest registration = new RegisterRequest(request.fullName(), email, request.phone(), RefreshTokens.newToken(),
                request.role());
        AuthTokens tokens = authService.register(registration, currentRefreshToken);

        User user = userRepository.findById(tokens.response().user().id()).orElseThrow();
        addLink(user, profile, false);   // a new account: the welcome email is enough
        flow.done();
        log.info("User id={} signed up with {}", user.getId(), profile.provider());
        return tokens;
    }

    @Override
    @Transactional
    public AuthTokens link(String browserToken, SocialLinkRequest request, String currentRefreshToken) {
        SocialLoginFlow flow = waitingFlow(browserToken);
        SocialProfile profile = flow.profile();
        User user = Optional.ofNullable(profile.email()).flatMap(userRepository::findByEmail).orElseThrow(SocialLoginServiceImpl::expired);
        checkMayUseSocialLogin(user);
        checkNoOtherAccountOfProvider(user, profile.provider());

        // The account's own password proves it is theirs: the same check, message and session as the login page
        AuthTokens tokens = authService.login(new LoginRequest(user.getEmail(), request.password()), currentRefreshToken);
        addLink(user, profile, true);
        flow.done();
        return tokens;
    }

    private AuthTokens logIn(User user, SocialLoginFlow flow, String currentRefreshToken) {
        checkMayUseSocialLogin(user);
        user.recordLogin(clock.instant());
        flow.done();
        log.info("User id={} logged in with {}", user.getId(), flow.getProvider());
        return loginSessions.start(user, currentRefreshToken);
    }

    /**
     * Links the provider account to the user. The provider verified the email and the user proved the account is theirs
     * (password, or an email verified on our side): the email counts as verified from now on.
     */
    private void addLink(User user, SocialProfile profile, boolean notifyUser) {
        Instant now = clock.instant();
        socialLogins.save(SocialLogin.link(user, profile.provider(), profile.providerUserId(), profile.email(), now));
        if (profile.emailVerified() && profile.email().equalsIgnoreCase(user.getEmail())) {
            user.markEmailVerified(now);
        }
        if (notifyUser) {
            // "Google was connected to your account. Not you? Reset your password." (notification-service)
            outboxEventPublisher.publish(KafkaTopics.USER_AGGREGATE, user.getId(),
                    userEventMapper.toSocialLoginLinked(user, providerName(profile.provider())));
        }
        log.info("User id={} connected {}", user.getId(), profile.provider());
    }

    /** Admin accounts keep the password only; a disabled account gets the login page's message. */
    private static void checkMayUseSocialLogin(User user) {
        if (user.isAdmin()) {
            throw new ForbiddenException("Admin accounts cannot use social login. Please log in with your password.");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("Your account is disabled. Please contact support.");
        }
    }

    /** One account per provider: an account already connected to another Google account does not get a second. */
    private void checkNoOtherAccountOfProvider(User user, String provider) {
        if (socialLogins.existsByUserIdAndProvider(user.getId(), provider)) {
            throw new DuplicateResourceException("This account is already connected to another " + providerName(provider)
                    + " account. Please log in with that one, or with your password.");
        }
    }

    /** The attempt of this browser that waits for the sign-up form or the password (locked: no double submit). */
    private SocialLoginFlow waitingFlow(String browserToken) {
        if (isBlank(browserToken)) {
            throw expired();
        }
        return flows.findWaitingForUser(Sha256.hex(browserToken), clock.instant()).orElseThrow(SocialLoginServiceImpl::expired);
    }

    private String providerName(String provider) {
        ClientRegistration registration = registrations.findByRegistrationId(provider);
        return registration == null ? provider : registration.getClientName();
    }

    private static BusinessException expired() {
        return new BusinessException("This sign-in has expired or was already used. Please try again.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
