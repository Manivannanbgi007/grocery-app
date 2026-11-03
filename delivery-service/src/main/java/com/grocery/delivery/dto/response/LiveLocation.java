package com.grocery.delivery.dto.response;

import java.time.Instant;

/**
 * The delivery partner's last known position.
 *
 * @param accuracyMeters how far off it may be (null when the phone did not say)
 * @param recordedAt     when the phone measured it
 * @param receivedAt     when the server got it (the customer page says "updated 20 s ago" from this)
 */
public record LiveLocation(double latitude, double longitude, Double accuracyMeters, Instant recordedAt, Instant receivedAt) {
}
