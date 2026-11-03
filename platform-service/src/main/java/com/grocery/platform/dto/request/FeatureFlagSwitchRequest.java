package com.grocery.platform.dto.request;

import jakarta.validation.constraints.NotNull;

public record FeatureFlagSwitchRequest(@NotNull Boolean enabled) {
}
