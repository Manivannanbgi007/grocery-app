package com.grocery.notification.mapper;

import com.grocery.notification.dto.response.NotificationResponse;
import com.grocery.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getEmailStatus(),
                notification.getCreatedAt());
    }
}
