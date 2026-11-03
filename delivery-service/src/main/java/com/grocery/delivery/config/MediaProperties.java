package com.grocery.delivery.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Settings under "app.media" in application.yml (proof-of-delivery photos).
 *
 * @param uploadDir     folder where the photos are saved (local disk for now; S3/MinIO later)
 * @param publicBaseUrl how clients reach them: the gateway routes /delivery-media/** to this service
 */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(@NotNull Path uploadDir, @NotBlank String publicBaseUrl) {
}
