package com.grocery.delivery.dto.response;

import java.time.Instant;

/**
 * @param onlineUntil when the partner goes offline by itself unless they call "go online" again (null when offline)
 */
public record AvailabilityResponse(boolean online, Instant onlineUntil) {
}
