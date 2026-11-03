package com.grocery.report.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The admin dashboard. "Today" is the India date.
 *
 * @param salesToday             total of today's orders that count as sales (confirmed, not cancelled/rejected/failed)
 * @param revenueToday           total of the orders DELIVERED today (the money actually earned today)
 * @param activeDeliveryPartners partners whose KYC is approved
 * @param activeCustomers        customers who ordered in the last 30 days
 * @param pendingRefunds         orders that ended without delivery while paid online, refund not done yet
 * @param averageDeliveryMinutes from placing to delivery, orders delivered in the last 30 days (null without any)
 */
public record DashboardResponse(
        LocalDate today,
        long totalOrders,
        long ordersToday,
        BigDecimal salesToday,
        BigDecimal revenueToday,
        long activeStores,
        long activeDeliveryPartners,
        long totalCustomers,
        long activeCustomers,
        long pendingRefunds,
        long outOfStockProducts,
        Integer averageDeliveryMinutes,
        Instant generatedAt) {
}
