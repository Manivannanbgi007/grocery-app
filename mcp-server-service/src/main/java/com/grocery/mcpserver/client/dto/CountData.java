package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A number from a service, e.g. the unread notifications (GET /api/v1/notifications/unread-count: {"count": 5}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountData(long count) {
}
