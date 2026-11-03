package com.grocery.auth.dto.request;

import com.grocery.auth.constant.ValidationPatterns;
import com.grocery.auth.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Finishes a sign-up with Google, Microsoft, Facebook or Apple: what the provider did not give")
public record SocialSignupRequest(

        @Schema(example = "Neha Gupta", description = "Pre-filled with the provider's name; may be changed")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "neha.gupta@example.com",
                description = "Only used when the provider gave no email or did not verify it; a verified one cannot be changed")
        @Email @Size(max = 150)
        String email,

        @Schema(example = "9876543210", description = "Required for every account (deliveries, the customer profile)")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,

        @Schema(description = "CUSTOMER, VENDOR or DELIVERY_PARTNER (as on the register page)", example = "CUSTOMER")
        @NotNull
        Role role) {
}
