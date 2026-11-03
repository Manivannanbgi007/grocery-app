package com.grocery.delivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Create or update a delivery zone.
 *
 * @param partnerPayout what a partner earns per delivery in this zone
 * @param active        optional, default true
 */
public record ZoneRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 60) String city,
        @NotEmpty @Size(max = 50) Set<@Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String> pincodes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal partnerPayout,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
