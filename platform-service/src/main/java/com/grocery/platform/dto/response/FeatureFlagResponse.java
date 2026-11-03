package com.grocery.platform.dto.response;

import java.time.Instant;

public record FeatureFlagResponse(String key, String description, boolean enabled, Long updatedByUserId, Instant updatedAt) {
}
