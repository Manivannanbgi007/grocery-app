package com.grocery.platform.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SettingsRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter currency code, e.g. INR") String currency,
        @NotNull @DecimalMin("0.00") @DecimalMax("500.00") @Digits(integer = 8, fraction = 2) BigDecimal deliveryFee,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal freeDeliveryFrom,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal minimumOrderValue,
        @NotBlank @Email @Size(max = 120) String supportEmail,
        @NotBlank @Size(max = 20) String supportPhone) {
}
