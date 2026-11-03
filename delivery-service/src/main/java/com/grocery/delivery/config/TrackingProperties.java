package com.grocery.delivery.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the live delivery tracking, under "app.tracking" in application.yml (docs/delivery-tracking.md).
 *
 * @param locationTtl       Redis forgets a partner's last position after this long without an update
 * @param minUpdateInterval updates that come faster are skipped (protects Redis and the customers' browsers)
 * @param maxLocationAge    older positions are refused (a phone that was offline for long sends stale data)
 * @param maxAccuracyMeters less precise positions are refused (a laptop without GPS guesses within kilometres)
 * @param heartbeatInterval every live stream gets a "keep-alive" this often, so proxies do not close it
 * @param streamTimeout     a stream is closed after this long; the browser simply opens a new one
 * @param maxStreams        live streams one instance keeps open at most (each one is an open connection)
 */
@Validated
@ConfigurationProperties(prefix = "app.tracking")
public record TrackingProperties(
        @NotNull Duration locationTtl,
        @NotNull Duration minUpdateInterval,
        @NotNull Duration maxLocationAge,
        @Positive double maxAccuracyMeters,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration streamTimeout,
        @Positive int maxStreams,
        @NotNull @Valid Routing routing) {

    /**
     * The optional routing service for the route along the streets and the arrival time.
     *
     * @param url                    an OSRM-compatible server, e.g. http://localhost:5000; empty = no routing
     *                               (the map draws a straight line and shows no arrival time)
     * @param timeout                how long one routing request may take (a slow router must not slow the update)
     * @param recalculateAfterMeters the route is computed again after the partner moved this far ...
     * @param recalculateAfter       ... or after this much time
     */
    public record Routing(
            String url,
            @NotNull Duration timeout,
            @Positive double recalculateAfterMeters,
            @NotNull Duration recalculateAfter) {

        public boolean enabled() {
            return url != null && !url.isBlank();
        }
    }
}
