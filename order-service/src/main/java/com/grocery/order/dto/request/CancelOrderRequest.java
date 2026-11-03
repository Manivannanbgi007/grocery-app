package com.grocery.order.dto.request;

import jakarta.validation.constraints.Size;

/**
 * @param reason optional, e.g. "Ordered by mistake"
 */
public record CancelOrderRequest(@Size(max = 255) String reason) {
}
