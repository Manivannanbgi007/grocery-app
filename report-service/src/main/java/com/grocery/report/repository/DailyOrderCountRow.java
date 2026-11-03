package com.grocery.report.repository;

import java.time.LocalDate;

/**
 * One row of a report query (filled by a JPQL "SELECT new ...").
 */
public record DailyOrderCountRow(LocalDate date, Long orders, Long cancelledOrders) {
}
