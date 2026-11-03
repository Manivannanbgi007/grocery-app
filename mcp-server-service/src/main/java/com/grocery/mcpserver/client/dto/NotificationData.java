package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * One of the user's own notifications from notification-service (GET /api/v1/notifications), only the fields the tools
 * need.
 *
 * @param type ORDER_CONFIRMED, NEW_ORDER, PAYMENT_FAILED ... (NotificationType in notification-service)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationData(String type, String title, String message, boolean read, Instant createdAt) {
}
