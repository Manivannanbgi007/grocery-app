package com.grocery.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The platform's commission per store, on the orders DELIVERED in the period.
 * Commission = items total x the store's commission % at the time of the order; the store gets the rest.
 */
public record CommissionReportResponse(LocalDate from, LocalDate to, List<StoreCommission> stores,
                                       BigDecimal totalItems, BigDecimal totalCommission, BigDecimal totalStorePayout) {

    public record StoreCommission(Long storeId, String storeName, long deliveredOrders, BigDecimal itemsTotal,
                                  BigDecimal commission, BigDecimal storePayout) {
    }
}
