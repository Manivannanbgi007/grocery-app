package com.grocery.store.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Settings under "app.store" in application.yml.
 *
 * @param timeZone                     time zone used for "is the store open now?" (stores are in India)
 * @param defaultCommissionPercentage  platform commission for a new store (the super admin can change it)
 * @param defaultOpeningTime           opening time used when a vendor registers without opening hours
 * @param defaultClosingTime           closing time used when a vendor registers without opening hours
 */
@Validated
@ConfigurationProperties(prefix = "app.store")
public record StoreProperties(
        @NotNull ZoneId timeZone,
        @NotNull @DecimalMin("0.00") @DecimalMax("50.00") BigDecimal defaultCommissionPercentage,
        @NotNull LocalTime defaultOpeningTime,
        @NotNull LocalTime defaultClosingTime) {
}
