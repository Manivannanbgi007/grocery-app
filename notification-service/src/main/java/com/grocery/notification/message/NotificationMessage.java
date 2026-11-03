package com.grocery.notification.message;

import com.grocery.notification.enums.NotificationType;

/**
 * The text of one notification, before we know who exactly receives it.
 *
 * @param referenceId the order, store or product it is about (null when none)
 */
public record NotificationMessage(NotificationType type, String title, String body, Long referenceId) {
}
