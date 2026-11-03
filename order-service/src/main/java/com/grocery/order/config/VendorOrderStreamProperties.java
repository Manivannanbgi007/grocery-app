package com.grocery.order.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the vendors' live order streams, under "app.vendor-order-stream" in application.yml (README "Live order
 * updates"). The keep-alive interval is there too: VendorOrderStreams.sendHeartbeats() reads it with @Scheduled.
 *
 * @param streamTimeout a stream is closed after this long; the browser simply opens a new one
 * @param maxStreams    live streams one instance keeps open at most (each one is an open connection)
 */
@Validated
@ConfigurationProperties(prefix = "app.vendor-order-stream")
public record VendorOrderStreamProperties(
        @NotNull Duration streamTimeout,
        @Positive int maxStreams) {
}
