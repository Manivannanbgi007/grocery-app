package com.grocery.report.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a report query (filled by a JPQL "SELECT new ...").
 */
public record DailySalesRow(LocalDate date, Long salesOrders, BigDecimal itemsTotal, BigDecimal couponDiscounts, BigDecimal deliveryFees, BigDecimal sales) {
}
