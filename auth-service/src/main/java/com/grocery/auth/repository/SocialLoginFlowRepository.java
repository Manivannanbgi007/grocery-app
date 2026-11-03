package com.grocery.auth.repository;

import com.grocery.auth.entity.SocialLoginFlow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Social login attempts. Every step moves the attempt to the next stage with ONE conditional UPDATE: of two requests
 * with the same state or code, only one gets the row (the other changes nothing and is refused), so each secret
 * works once.
 */
public interface SocialLoginFlowRepository extends JpaRepository<SocialLoginFlow, Long> {

    Optional<SocialLoginFlow> findByStateHash(String stateHash);

    Optional<SocialLoginFlow> findByCodeHash(String codeHash);

    /** The provider came back with this state: STARTED -> CALLBACK, only once and only before it expired. */
    @Modifying
    @Transactional
    @Query("""
            UPDATE SocialLoginFlow f SET f.stage = com.grocery.auth.entity.SocialLoginFlow.Stage.CALLBACK
            WHERE f.stateHash = :stateHash AND f.stage = com.grocery.auth.entity.SocialLoginFlow.Stage.STARTED
              AND f.expiresAt > :now
            """)
    int markCallback(String stateHash, Instant now);

    /**
     * The browser exchanges its one-time code: AUTHENTICATED -> REDEEMED, only for the browser that started the attempt,
     * only once and only before it expired. The attempt then stays open until newExpiresAt (sign-up or password form).
     */
    @Modifying
    @Query("""
            UPDATE SocialLoginFlow f SET f.stage = com.grocery.auth.entity.SocialLoginFlow.Stage.REDEEMED,
                   f.expiresAt = :newExpiresAt
            WHERE f.codeHash = :codeHash AND f.browserHash = :browserHash
              AND f.stage = com.grocery.auth.entity.SocialLoginFlow.Stage.AUTHENTICATED AND f.expiresAt > :now
            """)
    int redeem(String codeHash, String browserHash, Instant now, Instant newExpiresAt);

    /**
     * The attempt of this browser that waits for the sign-up form or the account's password. Locked until the end of the
     * transaction: a double click on "Create account" waits, then finds it DONE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f FROM SocialLoginFlow f
            WHERE f.browserHash = :browserHash AND f.stage = com.grocery.auth.entity.SocialLoginFlow.Stage.REDEEMED
              AND f.expiresAt > :now
            """)
    Optional<SocialLoginFlow> findWaitingForUser(String browserHash, Instant now);

    /** Deletes up to "limit" attempts that expired before the given time (HousekeepingJob). */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM social_login_flows WHERE expires_at < :before LIMIT :limit", nativeQuery = true)
    int deleteExpiredBefore(Instant before, int limit);
}
