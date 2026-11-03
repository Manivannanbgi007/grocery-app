package com.grocery.notification.dto.response;

/**
 * A single number, e.g. unread notifications (the badge on the bell icon).
 */
public record CountResponse(long count) {
}
