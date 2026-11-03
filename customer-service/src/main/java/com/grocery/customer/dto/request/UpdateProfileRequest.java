package com.grocery.customer.dto.request;

import com.grocery.customer.constant.ValidationPatterns;
import com.grocery.customer.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What a customer can change in their profile. The login email and password are changed in auth-service.
 */
public record UpdateProfileRequest(

        @Schema(example = "Priya Sharma")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(description = "Contact number for deliveries", example = "9000000004")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,

        @Schema(example = "1994-05-17")
        @Past
        LocalDate dateOfBirth,

        @Schema(example = "FEMALE")
        Gender gender) {
}
