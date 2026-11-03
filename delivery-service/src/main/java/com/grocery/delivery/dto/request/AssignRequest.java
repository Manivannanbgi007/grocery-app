package com.grocery.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param partnerUserId the delivery partner (their user id)
 */
public record AssignRequest(@NotNull @Positive Long partnerUserId) {
}
