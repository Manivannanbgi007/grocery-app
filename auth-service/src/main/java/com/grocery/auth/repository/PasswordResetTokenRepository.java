package com.grocery.auth.repository;

import com.grocery.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** The token whose SHA-256 hash this is (Sha256.hex of what the user typed). */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks every unused token of a user as used: a newer token, a reset or a password change makes the older ones
     * worthless. flushAutomatically: changes made before (a token marked used) are written first.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user.id = :userId AND t.used = false")
    int invalidateAllOfUser(Long userId);

    /** Deletes up to "limit" tokens that expired before the given time (HousekeepingJob). */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM password_reset_tokens WHERE expires_at < :before LIMIT :limit", nativeQuery = true)
    int deleteExpiredBefore(Instant before, int limit);
}
