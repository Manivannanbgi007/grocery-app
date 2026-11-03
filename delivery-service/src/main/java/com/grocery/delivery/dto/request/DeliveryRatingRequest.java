package com.grocery.delivery.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The customer's rating of the delivery of one order.
 */
public record DeliveryRatingRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment) {
}
