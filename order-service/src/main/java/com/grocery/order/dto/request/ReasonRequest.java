package com.grocery.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A required reason, e.g. why a store rejects an order or why an admin cancels it.
 */
public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {
}
