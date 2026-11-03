package com.grocery.notification.service;

import com.grocery.notification.dto.common.PageResponse;
import com.grocery.notification.dto.response.CountResponse;
import com.grocery.notification.dto.response.NotificationResponse;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import org.springframework.data.domain.Pageable;

/**
 * Reading notifications: a user's own (the bell icon) and, for admins, everyone's.
 */
public interface NotificationService {

    PageResponse<NotificationResponse> listMyNotifications(Long userId, boolean unreadOnly, Pageable pageable);

    CountResponse countUnread(Long userId);

    NotificationResponse markRead(Long userId, Long notificationId);

    /** @return how many were marked read */
    CountResponse markAllRead(Long userId);

    /** Admin: every filter is optional. */
    PageResponse<NotificationResponse> searchNotifications(Long userId, NotificationType type, EmailStatus emailStatus, Pageable pageable);
}
