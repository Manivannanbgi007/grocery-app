package com.grocery.order.dto.response;

import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderStatus;

import java.time.Instant;

/**
 * One step of the tracking timeline, e.g. PACKED "Your items are packed" by VENDOR at 10:42.
 */
public record TimelineEntryResponse(OrderStatus status, String note, ChangedBy changedBy, Instant at) {
}
