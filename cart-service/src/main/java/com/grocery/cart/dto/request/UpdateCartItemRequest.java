package com.grocery.cart.dto.request;

import com.grocery.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param quantity the new quantity (to remove an item, use DELETE)
 */
public record UpdateCartItemRequest(@NotNull @Min(1) @Max(CartItem.MAX_QUANTITY) Integer quantity) {
}
