package com.grocery.report.repository;

import java.math.BigDecimal;

/**
 * One row of a report query (filled by a JPQL "SELECT new ...").
 */
public record ProductSalesRow(Long productId, String productName, Long quantity, BigDecimal sales) {
}
