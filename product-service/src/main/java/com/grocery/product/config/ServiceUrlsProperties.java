package com.grocery.product.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Base URLs of the services product-service calls (app.services in application.yml).
 */
@Validated
@ConfigurationProperties(prefix = "app.services")
public record ServiceUrlsProperties(@NotBlank String orderService) {
}
