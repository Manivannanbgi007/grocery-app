package com.grocery.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(example = "customer1@grocery.com")
        @NotBlank @Email
        String email,

        @Schema(example = "Password@123")
        @NotBlank
        String password) {
}
