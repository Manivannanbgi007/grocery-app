package com.grocery.delivery.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Settings under "app.delivery" in application.yml.
 *
 * @param onlineFor how long "I am online" lasts; the partner app repeats the call to stay online
 *                  (if the phone dies, the partner goes offline by itself and gets no new deliveries)
 * @param timeZone  the day boundaries of the earnings report
 */
@Validated
@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(@NotNull Duration onlineFor, @NotNull ZoneId timeZone) {
}
