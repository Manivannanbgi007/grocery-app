package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The admin dashboard's numbers (GET /api/v1/admin/reports/dashboard, AdminDashboardResponse of report-service).
 *
 * @param salesToday             today's orders that count as sales (confirmed, not cancelled, rejected or failed)
 * @param revenueToday           the orders delivered today
 * @param activeDeliveryPartners partners whose KYC is approved
 * @param activeCustomers        customers who ordered in the last 30 days
 * @param pendingRefunds         paid online, ended without delivery, refund not done yet
 * @param averageDeliveryMinutes from placing to delivery over the last 30 days; null without delivered orders
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminDashboardData(LocalDate today, long totalOrders, long ordersToday, BigDecimal salesToday,
                                 BigDecimal revenueToday, long activeStores, long activeDeliveryPartners,
                                 long totalCustomers, long activeCustomers, long pendingRefunds,
                                 long outOfStockProducts, BigDecimal averageDeliveryMinutes) {
}
