package com.grocery.payment.dto.response;

import com.grocery.payment.repository.PaymentTotals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin payment summary for the payments created from "from" to "to" (both days included, India time).
 *
 * @param paidOnline    ONLINE payments that are PAID (kept)
 * @param collectedCash COD payments whose cash was COLLECTED at delivery
 * @param refunded      money received and given back again (REFUNDED)
 * @param received      paidOnline + collectedCash: the money the platform keeps
 * @param totals        count and sum for every method and status, including PENDING, FAILED and CANCELLED
 */
public record PaymentSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal paidOnline,
        BigDecimal collectedCash,
        BigDecimal refunded,
        BigDecimal received,
        List<PaymentTotals> totals) {
}
