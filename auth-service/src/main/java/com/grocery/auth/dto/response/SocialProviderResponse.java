package com.grocery.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A provider users can log in with: the shop shows a button for each one. */
public record SocialProviderResponse(
        @Schema(example = "google") String id,
        @Schema(example = "Google") String name) {
}
