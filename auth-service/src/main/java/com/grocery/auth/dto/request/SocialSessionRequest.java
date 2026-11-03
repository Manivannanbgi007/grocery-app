package com.grocery.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The one-time code the shop page got after the provider's login (valid 60 seconds, once)")
public record SocialSessionRequest(
        @NotBlank @Size(max = 100) String code) {
}
