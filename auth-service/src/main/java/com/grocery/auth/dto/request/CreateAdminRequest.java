package com.grocery.auth.dto.request;

import com.grocery.auth.constant.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Super admin creates a new platform admin")
public record CreateAdminRequest(

        @Schema(example = "Operations Admin")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "ops.admin@grocery.com")
        @NotBlank @Email @Size(max = 150)
        String email,

        @Schema(example = "9123456780")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,

        @Schema(example = "Password@123")
        @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password) {
}
