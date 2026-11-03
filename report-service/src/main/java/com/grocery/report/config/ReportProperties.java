package com.grocery.report.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Settings under "app.report" in application.yml.
 *
 * @param timeZone what "today" and the days of a report mean (Asia/Kolkata)
 */
@Validated
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(@NotNull ZoneId timeZone) {
}
