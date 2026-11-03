package com.grocery.platform.dto.response;

import java.time.Instant;

/**
 * @param live true when customers see it right now (active and inside its time window)
 */
public record BannerResponse(
        Long id,
        String title,
        String subtitle,
        String imageUrl,
        String linkUrl,
        int position,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        boolean live) {
}
