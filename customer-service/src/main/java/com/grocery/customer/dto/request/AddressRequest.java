package com.grocery.customer.dto.request;

import com.grocery.customer.constant.ValidationPatterns;
import com.grocery.customer.enums.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A delivery address, used to add a new address and to update an existing one.
 */
public record AddressRequest(

        @Schema(example = "HOME")
        @NotNull
        AddressLabel label,

        @Schema(example = "Priya Sharma")
        @NotBlank @Size(max = 100)
        String contactName,

        @Schema(example = "9000000004")
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String contactPhone,

        @Schema(example = "Flat 302, Lakeview Apartments")
        @NotBlank @Size(max = 150)
        String addressLine1,

        @Schema(example = "2nd Main Road, HAL 2nd Stage")
        @Size(max = 150)
        String addressLine2,

        @Schema(example = "Near Indiranagar Metro")
        @Size(max = 100)
        String landmark,

        @Schema(example = "Bengaluru")
        @NotBlank @Size(max = 60)
        String city,

        @Schema(example = "Karnataka")
        @NotBlank @Size(max = 60)
        String state,

        @Schema(example = "560038")
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = ValidationPatterns.PINCODE_MESSAGE)
        String pincode,

        @Schema(example = "12.971900")
        @DecimalMin("-90.0") @DecimalMax("90.0")
        BigDecimal latitude,

        @Schema(example = "77.641200")
        @DecimalMin("-180.0") @DecimalMax("180.0")
        BigDecimal longitude,

        @Schema(description = "Make this the default delivery address (optional, default false)", example = "false")
        Boolean setAsDefault) {

    /** Optional flag: a missing value means false. */
    public boolean wantsDefault() {
        return Boolean.TRUE.equals(setAsDefault);
    }
}
