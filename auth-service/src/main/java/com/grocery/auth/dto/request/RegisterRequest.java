package com.grocery.auth.dto.request;

import com.grocery.auth.constant.ValidationPatterns;
import com.grocery.auth.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Self-registration for customers, vendors and delivery partners")
public record RegisterRequest(

        @Schema(example = "Neha Gupta")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "neha.gupta@example.com")
        @NotBlank @Email @Size(max = 150)
        String email,

        @Schema(example = "9876543210")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,

        @Schema(example = "Password@123")
        @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,

        @Schema(description = "CUSTOMER, VENDOR or DELIVERY_PARTNER", example = "CUSTOMER")
        @NotNull
        Role role) {
}
