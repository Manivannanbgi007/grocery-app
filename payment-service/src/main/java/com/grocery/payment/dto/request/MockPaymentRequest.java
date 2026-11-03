package com.grocery.payment.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Pretend to pay with the mock gateway.
 *
 * @param success true = the payment goes through, false = the bank declines it
 */
public record MockPaymentRequest(@NotNull Boolean success) {
}
