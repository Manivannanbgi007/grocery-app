package com.grocery.notification.entity;

import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One message for one user: shown in the app (the bell icon), and also sent by email when we know
 * the user's address. emailStatus records what happened to the email.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Notification.create(...)
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String message;

    /** The order, store or product the notification is about (null when none). */
    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "email_to", length = 150)
    private String emailTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private EmailStatus emailStatus;

    public static Notification create(Long userId, NotificationType type, String title, String message, Long referenceId) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.message = message;
        notification.referenceId = referenceId;
        notification.read = false;
        notification.emailStatus = EmailStatus.NOT_SENT;
        return notification;
    }

    public void recordEmail(String emailTo, EmailStatus emailStatus) {
        this.emailTo = emailTo;
        this.emailStatus = emailStatus;
    }

    public void markRead(Instant readAt) {
        if (!read) {
            this.read = true;
            this.readAt = readAt;
        }
    }
}
