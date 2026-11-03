package com.grocery.cart.dto.request;

import com.grocery.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param quantity units to add (added to the quantity already in the cart)
 */
public record AddCartItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(CartItem.MAX_QUANTITY) Integer quantity) {
}
