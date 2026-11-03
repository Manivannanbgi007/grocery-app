package com.grocery.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "app.notification" in application.yml.
 *
 * @param fromEmail sender address of every email
 */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(@NotBlank @Email String fromEmail) {
}
