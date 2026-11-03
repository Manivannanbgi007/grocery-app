package com.grocery.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param orderId the delivered order the product came with (proves the customer bought it)
 */
public record ReviewRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment) {
}
