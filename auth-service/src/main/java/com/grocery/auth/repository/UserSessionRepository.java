package com.grocery.auth.repository;

import com.grocery.auth.entity.UserSession;
import com.grocery.auth.enums.SessionEndReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * The session whose CURRENT token has this hash, locked until the transaction ends (SELECT ... FOR UPDATE):
     * two refreshes with the same token are handled one after the other, never both at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.tokenHash = :tokenHash")
    Optional<UserSession> findByTokenHashForUpdate(String tokenHash);

    /** The session whose PREVIOUS token has this hash (an old token that came back), locked like above. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.previousTokenHash = :tokenHash")
    Optional<UserSession> findByPreviousTokenHashForUpdate(String tokenHash);

    Optional<UserSession> findByTokenHash(String tokenHash);

    /** Ends every open session of a user, except the one with id keepSessionId (null = end all). */
    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason
            WHERE s.userId = :userId AND s.revokedAt IS NULL AND (:keepSessionId IS NULL OR s.id <> :keepSessionId)
            """)
    int revokeAllOfUser(Long userId, SessionEndReason reason, Instant now, Long keepSessionId);

    /** Deletes up to "limit" sessions that ended (expired or were revoked) before the given time (HousekeepingJob). */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM user_sessions WHERE expires_at < :before OR revoked_at < :before LIMIT :limit",
            nativeQuery = true)
    int deleteEndedBefore(Instant before, int limit);
}
