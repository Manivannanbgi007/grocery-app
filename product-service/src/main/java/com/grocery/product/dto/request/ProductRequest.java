package com.grocery.product.dto.request;

import com.grocery.product.constant.ValidationPatterns;
import com.grocery.product.enums.Unit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A product as the vendor enters it. Used to create a product and to update it.
 */
public record ProductRequest(

        @Schema(example = "1")
        @NotNull
        Long categoryId,

        @Schema(example = "Cherry Tomato")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(example = "Sweet cherry tomatoes, great for salads")
        @Size(max = 1000)
        String description,

        @Schema(example = "Fresho")
        @Size(max = 60)
        String brand,

        @Schema(description = "Your own product code, unique in your store", example = "FM-VEG-020")
        @NotBlank @Pattern(regexp = ValidationPatterns.SKU, message = ValidationPatterns.SKU_MESSAGE)
        String sku,

        @Schema(example = "G")
        @NotNull
        Unit unit,

        @Schema(description = "With unit: 250 G = a 250 gram pack", example = "250")
        @NotNull @Positive @Digits(integer = 7, fraction = 3)
        BigDecimal unitQuantity,

        @Schema(description = "Price before discount (MRP) in INR", example = "45.00")
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @Schema(description = "Discount in percent (optional, default 0)", example = "10")
        @DecimalMin("0.00") @DecimalMax("90.00") @Digits(integer = 2, fraction = 2)
        BigDecimal discountPercentage,

        @Schema(description = "GST in percent", example = "0")
        @NotNull @DecimalMin("0.00") @DecimalMax("28.00") @Digits(integer = 2, fraction = 2)
        BigDecimal taxPercentage) {

    /** Optional field: a missing discount means no discount. */
    public BigDecimal discountOrZero() {
        return discountPercentage == null ? BigDecimal.ZERO : discountPercentage;
    }
}
