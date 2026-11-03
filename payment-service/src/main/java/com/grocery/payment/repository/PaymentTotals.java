package com.grocery.payment.repository;

import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * One row of the admin payment summary: how many payments of one method and status, and their sum.
 * Filled by the JPQL "SELECT new ..." in PaymentRepository.
 */
public record PaymentTotals(PaymentMethod method, PaymentStatus status, Long count, BigDecimal amount) {
}
