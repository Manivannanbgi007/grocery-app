package com.grocery.inventory.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.inventory" in application.yml.
 *
 * @param defaultLowStockThreshold used when the vendor does not choose a threshold for a product
 */
@Validated
@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(@Min(0) int defaultLowStockThreshold) {
}
