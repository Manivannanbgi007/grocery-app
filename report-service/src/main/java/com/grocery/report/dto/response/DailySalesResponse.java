package com.grocery.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales per day (by the day the order was placed). Days without orders are included with zeros.
 *
 * @param storeId null = all stores
 */
public record DailySalesResponse(LocalDate from, LocalDate to, Long storeId, List<Day> days, BigDecimal totalSales, long totalOrders) {

    /**
     * @param orders          all orders placed that day
     * @param cancelledOrders cancelled, rejected or not delivered
     * @param salesOrders     orders that count as sales
     * @param sales           what the customers paid for those (items - coupons + delivery fees)
     */
    public record Day(LocalDate date, long orders, long cancelledOrders, long salesOrders, BigDecimal itemsTotal,
                      BigDecimal couponDiscounts, BigDecimal deliveryFees, BigDecimal sales) {
    }
}
