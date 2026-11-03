package com.grocery.notification.service.impl;

import com.grocery.notification.dto.common.PageResponse;
import com.grocery.notification.dto.response.CountResponse;
import com.grocery.notification.dto.response.NotificationResponse;
import com.grocery.notification.entity.Notification;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import com.grocery.notification.exception.ResourceNotFoundException;
import com.grocery.notification.mapper.NotificationMapper;
import com.grocery.notification.repository.NotificationRepository;
import com.grocery.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMyNotifications(Long userId, boolean unreadOnly, Pageable pageable) {
        return PageResponse.from(notificationRepository.findForUser(userId, unreadOnly, pageable).map(notificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CountResponse countUnread(Long userId) {
        return new CountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead(Instant.now());
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public CountResponse markAllRead(Long userId) {
        return new CountResponse(notificationRepository.markAllRead(userId, Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> searchNotifications(Long userId, NotificationType type, EmailStatus emailStatus,
                                                                  Pageable pageable) {
        return PageResponse.from(notificationRepository.search(userId, type, emailStatus, pageable).map(notificationMapper::toResponse));
    }
}
