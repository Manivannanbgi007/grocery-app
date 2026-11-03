package com.grocery.store.dto.request;

import com.grocery.store.constant.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Store details a vendor fills in when registering, and when editing the store later.
 */
@Schema(description = "Store details entered by the vendor")
public record StoreDetailsRequest(

        @Schema(example = "Daily Needs Supermarket")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(example = "Fresh vegetables, fruits and daily essentials")
        @Size(max = 500)
        String description,

        @Schema(example = "9845012345")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,

        @Schema(example = "dailyneeds@example.com")
        @Email @Size(max = 150)
        String email,

        @Schema(example = "12, 5th Cross, Indiranagar")
        @NotBlank @Size(max = 150)
        String addressLine1,

        @Schema(example = "Near Metro Station")
        @Size(max = 150)
        String addressLine2,

        @Schema(example = "Bengaluru")
        @NotBlank @Size(max = 60)
        String city,

        @Schema(example = "Karnataka")
        @NotBlank @Size(max = 60)
        String state,

        @Schema(example = "560038")
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = ValidationPatterns.PINCODE_MESSAGE)
        String pincode,

        @Schema(example = "12.978400")
        @DecimalMin("-90.0") @DecimalMax("90.0")
        BigDecimal latitude,

        @Schema(example = "77.640800")
        @DecimalMin("-180.0") @DecimalMax("180.0")
        BigDecimal longitude) {
}
