package com.grocery.auth.service.impl;

import com.grocery.auth.config.SessionProperties;
import com.grocery.auth.entity.User;
import com.grocery.auth.entity.UserSession;
import com.grocery.auth.enums.SessionEndReason;
import com.grocery.auth.exception.SessionEndedException;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.repository.UserSessionRepository;
import com.grocery.auth.security.RefreshTokens;
import com.grocery.auth.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Refresh with rotation and reuse detection:
 *
 *   the token is the CURRENT one of a session   -> replace it (new token, new expiry), new access token
 *   it is the PREVIOUS one, replaced < 10 s ago -> a second tab refreshed at the same moment: new access token only
 *   it is the PREVIOUS one, replaced earlier    -> REUSE: someone has an old copy (stolen?): end the session
 *   unknown, expired or revoked                 -> 401, log in again
 *
 * The session row is locked (SELECT ... FOR UPDATE) for the whole refresh, so two refreshes with the same token are
 * handled one after the other. READ COMMITTED: the second one then sees what the first one committed (the token was
 * replaced) and takes the "previous token" path. noRollbackFor: a session ended here (reuse, disabled user) stays
 * ended although a 401 is thrown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedToken start(User user) {
        String token = RefreshTokens.newToken();
        UserSession session = sessionRepository.save(UserSession.start(user.getId(), RefreshTokens.hash(token),
                clock.instant(), properties.refreshTokenLifetime(), properties.maxLifetime()));
        log.info("Session id={} started for user id={}", session.getId(), user.getId());
        return new IssuedToken(token, session.getExpiresAt());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = SessionEndedException.class)
    public Refreshed refresh(String token) {
        if (!StringUtils.hasText(token)) {
            throw new SessionEndedException();   // no cookie: no need to ask the database
        }
        String hash = RefreshTokens.hash(token);
        Instant now = clock.instant();

        Optional<UserSession> current = sessionRepository.findByTokenHashForUpdate(hash);
        if (current.isEmpty()) {
            return refreshWithReplacedToken(hash, now);
        }
        UserSession session = current.get();
        if (session.isRevoked() || session.isExpired(now)) {
            throw new SessionEndedException();
        }
        User user = activeUserOf(session, now);

        String newToken = RefreshTokens.newToken();
        session.rotate(RefreshTokens.hash(newToken), now, properties.refreshTokenLifetime());
        return new Refreshed(user, new IssuedToken(newToken, session.getExpiresAt()));
    }

    /** The token is not the current one of any session: replaced a moment ago, an old copy, or unknown. */
    private Refreshed refreshWithReplacedToken(String hash, Instant now) {
        UserSession session = sessionRepository.findByPreviousTokenHashForUpdate(hash)
                .orElseThrow(SessionEndedException::new);
        if (session.isRevoked() || session.isExpired(now)) {
            throw new SessionEndedException();
        }
        if (session.wasRotatedWithin(properties.reuseGrace(), now)) {
            // Two tabs refreshed at the same moment; the other one already received the new token (the browser shares
            // the cookie): an access token only, the cookie stays as it is.
            return new Refreshed(activeUserOf(session, now), null);
        }
        session.revoke(SessionEndReason.REUSE_DETECTED, now);
        log.warn("An old refresh token of session id={} (user id={}) was used again after it had been replaced: "
                + "the session is ended, the user must log in again", session.getId(), session.getUserId());
        throw new SessionEndedException();
    }

    /** The session's user, if still allowed to use the platform; otherwise the session ends. */
    private User activeUserOf(UserSession session, Instant now) {
        Optional<User> user = userRepository.findById(session.getUserId()).filter(User::isActive);
        if (user.isEmpty()) {
            session.revoke(SessionEndReason.USER_DISABLED, now);
            throw new SessionEndedException();
        }
        return user.get();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void end(String token, SessionEndReason reason) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String hash = RefreshTokens.hash(token);
        sessionRepository.findByTokenHashForUpdate(hash)
                .or(() -> sessionRepository.findByPreviousTokenHashForUpdate(hash))
                .filter(session -> !session.isRevoked())
                .ifPresent(session -> {
                    session.revoke(reason, clock.instant());
                    log.info("Session id={} of user id={} ended: {}", session.getId(), session.getUserId(), reason);
                });
    }

    @Override
    @Transactional
    public void endAll(Long userId, SessionEndReason reason) {
        endAllExcept(userId, null, reason);
    }

    @Override
    @Transactional
    public void endAllExcept(Long userId, String keepToken, SessionEndReason reason) {
        // The session to keep must really belong to this user (the cookie could be anything)
        Long keepSessionId = !StringUtils.hasText(keepToken) ? null
                : sessionRepository.findByTokenHash(RefreshTokens.hash(keepToken))
                        .filter(session -> session.getUserId().equals(userId) && !session.isRevoked())
                        .map(UserSession::getId)
                        .orElse(null);
        int ended = sessionRepository.revokeAllOfUser(userId, reason, clock.instant(), keepSessionId);
        if (ended > 0) {
            log.info("Ended {} session(s) of user id={}: {}", ended, userId, reason);
        }
    }
}
