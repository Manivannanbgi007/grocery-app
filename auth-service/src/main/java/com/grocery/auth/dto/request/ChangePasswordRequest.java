package com.grocery.auth.dto.request;

import com.grocery.auth.constant.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @Schema(example = "Password@123")
        @NotBlank
        String currentPassword,

        @Schema(example = "NewPassword@456")
        @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword) {
}
