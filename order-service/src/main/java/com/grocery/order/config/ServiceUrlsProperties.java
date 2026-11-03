package com.grocery.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.services" in application.yml: where the services this one calls are.
 * Locally they are localhost ports; in Kubernetes they become service names (http://cart-service:8080).
 */
@Validated
@ConfigurationProperties(prefix = "app.services")
public record ServiceUrlsProperties(
        @NotBlank String cartService,
        @NotBlank String customerService,
        @NotBlank String storeService) {
}
