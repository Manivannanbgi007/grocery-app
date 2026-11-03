package com.grocery.inventory.dto.request;

import com.grocery.inventory.entity.Stock;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * New goods arrived: add them to the current stock.
 *
 * @param quantity units received
 */
public record AddStockRequest(@NotNull @Min(1) @Max(Stock.MAX_QUANTITY) Integer quantity) {
}
