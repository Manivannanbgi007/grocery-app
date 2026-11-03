package com.grocery.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCommissionRequest(

        @Schema(description = "Platform commission in percent (0 - 50)", example = "12.50")
        @NotNull @DecimalMin("0.00") @DecimalMax("50.00") @Digits(integer = 2, fraction = 2)
        BigDecimal commissionPercentage) {
}
