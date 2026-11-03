package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales per day of a store (GET /api/v1/vendor/reports/sales/daily) or of the whole platform
 * (GET /api/v1/admin/reports/sales/daily), DailySalesResponse of report-service. Days without orders have zeros.
 *
 * @param totalSales what customers paid for the orders that count (confirmed, not cancelled, rejected or failed)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailySalesData(LocalDate from, LocalDate to, List<Day> days, BigDecimal totalSales, long totalOrders) {

    /**
     * @param cancelledOrders cancelled, rejected or not delivered
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(LocalDate date, int orders, int cancelledOrders, BigDecimal sales) {
    }
}
