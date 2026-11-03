package com.grocery.inventory.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.services" in application.yml: where the other services this one calls are.
 * Locally they are localhost ports; in Kubernetes they become service names (http://product-read-service:8080).
 *
 * @param productReadService base URL of product-read-service
 */
@Validated
@ConfigurationProperties(prefix = "app.services")
public record ServiceUrlsProperties(@NotBlank String productReadService) {
}
