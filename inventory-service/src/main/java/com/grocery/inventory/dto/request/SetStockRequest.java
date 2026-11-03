package com.grocery.inventory.dto.request;

import com.grocery.inventory.entity.Stock;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Set a product's stock to an exact count (for example after counting the shelf).
 *
 * @param quantityOnHand    units in the store now
 * @param lowStockThreshold optional: alert when the available units drop to this number.
 *                          Left out = keep the current threshold (or use the default for a new product).
 */
public record SetStockRequest(
        @NotNull @Min(0) @Max(Stock.MAX_QUANTITY) Integer quantityOnHand,
        @Min(0) @Max(Stock.MAX_QUANTITY) Integer lowStockThreshold) {
}
