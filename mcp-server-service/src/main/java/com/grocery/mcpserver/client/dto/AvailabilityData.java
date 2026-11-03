package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Whether a delivery partner is online (GET /api/v1/delivery/availability). Online lasts 30 minutes and is renewed
 * while the partner's panel is open.
 *
 * @param onlineUntil when "online" runs out; null while offline
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailabilityData(boolean online, Instant onlineUntil) {
}
