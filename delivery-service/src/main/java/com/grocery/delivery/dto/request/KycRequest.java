package com.grocery.delivery.dto.request;

import com.grocery.delivery.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The partner's verification details. An admin checks and approves them.
 *
 * @param vehicleNumber Indian registration number, e.g. KA01AB1234
 * @param zoneId        the delivery zone the partner wants to work in
 */
public record KycRequest(
        @NotNull VehicleType vehicleType,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$", message = "must look like KA01AB1234") String vehicleNumber,
        @NotBlank @Size(min = 5, max = 30) String drivingLicenseNumber,
        @NotNull @Positive Long zoneId) {
}
