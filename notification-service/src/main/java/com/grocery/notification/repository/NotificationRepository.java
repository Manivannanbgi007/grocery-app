package com.grocery.notification.repository;

import com.grocery.notification.entity.Notification;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** A user's notifications; unreadOnly = true keeps only the unread ones. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (:unreadOnly = false OR n.read = false)
            """)
    Page<Notification> findForUser(Long userId, boolean unreadOnly, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    /** User access: only their own notifications. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.userId = :userId AND n.read = false")
    int markAllRead(Long userId, Instant now);

    /** Admin search: every filter is optional (null = ignore it). */
    @Query("""
            SELECT n FROM Notification n
            WHERE (:userId IS NULL OR n.userId = :userId)
              AND (:type IS NULL OR n.type = :type)
              AND (:emailStatus IS NULL OR n.emailStatus = :emailStatus)
            """)
    Page<Notification> search(Long userId, NotificationType type, EmailStatus emailStatus, Pageable pageable);
}
