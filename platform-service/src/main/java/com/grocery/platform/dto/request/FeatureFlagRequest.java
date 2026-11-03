package com.grocery.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param key UPPER_SNAKE_CASE, e.g. CITY_PUNE
 */
public record FeatureFlagRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,59}$", message = "must be UPPER_SNAKE_CASE, e.g. CITY_PUNE") String key,
        @NotBlank @Size(max = 255) String description,
        @NotNull Boolean enabled) {
}
