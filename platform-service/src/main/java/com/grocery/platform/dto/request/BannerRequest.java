package com.grocery.platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param startsAt optional: shown from (null = right away)
 * @param endsAt   optional: shown until (null = no end)
 */
public record BannerRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 200) String subtitle,
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 500) String linkUrl,
        @NotNull @Min(1) @Max(100) Integer position,
        Instant startsAt,
        Instant endsAt,
        @NotNull Boolean active) {
}
