package com.grocery.notification.dto.response;

import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;

import java.time.Instant;

/**
 * @param referenceId the order, store or product it is about (null when none)
 */
public record NotificationResponse(
        Long id,
        Long userId,
        NotificationType type,
        String title,
        String message,
        Long referenceId,
        boolean read,
        EmailStatus emailStatus,
        Instant createdAt) {
}
