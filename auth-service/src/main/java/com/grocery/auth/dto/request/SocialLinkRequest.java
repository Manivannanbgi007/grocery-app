package com.grocery.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "The password of the existing account with the provider's email, to connect the provider account once")
public record SocialLinkRequest(
        @NotBlank String password) {
}
