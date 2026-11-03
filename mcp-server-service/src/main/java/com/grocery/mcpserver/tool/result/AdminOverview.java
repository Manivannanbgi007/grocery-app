package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.AdminDashboardData;
import com.grocery.mcpserver.tool.Formats;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What getAdminOverview returns: the dashboard's numbers and the work waiting for the platform team.
 *
 * @param salesToday               today's orders that count as sales
 * @param revenueToday             the orders delivered today
 * @param activeDeliveryPartners   partners whose KYC is approved
 * @param activeCustomers          e.g. "3 of 139 customers ordered in the last 30 days (2%)"
 * @param pendingRefunds           paid online, ended without delivery, refund not done yet
 * @param averageDeliveryTime      from placing to delivery over the last 30 days
 * @param storesToApprove          new stores waiting for an approval
 * @param kycToReview              delivery partners whose KYC waits for a review
 * @param deliveriesWithoutPartner ready orders that no delivery partner took
 */
public record AdminOverview(String date, long ordersToday, String salesToday, String revenueToday, long totalOrders,
                            long activeStores, long activeDeliveryPartners, String activeCustomers,
                            long pendingRefunds, long outOfStockProducts, String averageDeliveryTime,
                            long storesToApprove, long kycToReview, long deliveriesWithoutPartner) {

    public static AdminOverview from(AdminDashboardData dashboard, long storesToApprove, long kycToReview,
                                     long deliveriesWithoutPartner) {
        return new AdminOverview(Formats.day(dashboard.today()), dashboard.ordersToday(),
                Formats.rupees(dashboard.salesToday()), Formats.rupees(dashboard.revenueToday()), dashboard.totalOrders(),
                dashboard.activeStores(), dashboard.activeDeliveryPartners(),
                activeCustomers(dashboard.activeCustomers(), dashboard.totalCustomers()),
                dashboard.pendingRefunds(), dashboard.outOfStockProducts(), deliveryTime(dashboard.averageDeliveryMinutes()),
                storesToApprove, kycToReview, deliveriesWithoutPartner);
    }

    /** The share is written out here: the model must not calculate it (and the answer check rejects a % no tool returned). */
    private static String activeCustomers(long active, long total) {
        String text = active + " of " + total + " customers ordered in the last 30 days";
        if (total == 0) {
            return text;
        }
        long percent = BigDecimal.valueOf(active * 100).divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).longValue();
        return text + " (" + percent + "%)";
    }

    private static String deliveryTime(BigDecimal minutes) {
        return minutes == null
                ? "No delivered orders in the last 30 days"
                : minutes.setScale(0, RoundingMode.HALF_UP).toPlainString() + " min from order to door (last 30 days)";
    }
}
