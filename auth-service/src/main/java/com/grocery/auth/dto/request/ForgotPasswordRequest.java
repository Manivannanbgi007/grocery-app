package com.grocery.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(

        @Schema(example = "customer2@grocery.com")
        @NotBlank @Email
        String email) {
}
