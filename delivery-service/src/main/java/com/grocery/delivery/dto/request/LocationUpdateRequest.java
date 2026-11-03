package com.grocery.delivery.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * Where the delivery partner is now, as the phone's GPS (the browser's Geolocation API) reported it.
 * Example: { "latitude": 12.9752, "longitude": 77.6401, "accuracyMeters": 12.5, "recordedAt": "2026-10-27T10:15:30Z" }
 *
 * @param accuracyMeters how far off the position may be (the GPS's own estimate); optional
 * @param recordedAt     when the phone measured it (not when it arrived: a phone that was offline sends later)
 */
public record LocationUpdateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @PositiveOrZero Double accuracyMeters,
        @NotNull Instant recordedAt) {
}
