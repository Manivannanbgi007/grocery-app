package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.client.dto.TopStoreData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;

/**
 * What getVendorSales (one store) and getAdminSales (the platform) return: the totals of a period and the best sellers.
 *
 * @param period          e.g. "the last 7 days, 21 Oct 2026 to 27 Oct 2026"
 * @param sales           what customers paid for the orders that count (confirmed, not cancelled, rejected or failed)
 * @param orders          all orders placed in the period
 * @param cancelledOrders cancelled, rejected or not delivered
 * @param bestDay         the day with the highest sales; null without sales
 * @param topStores       admins only: the stores with the highest sales
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesSummary(String period, String sales, long orders, long cancelledOrders, String bestDay,
                           List<Seller> bestSellers, List<Store> topStores) {

    /** @param sold units sold */
    public record Seller(String product, int sold, String sales) {
    }

    public record Store(String store, int orders, String sales) {
    }

    public static SalesSummary from(String period, DailySalesData sales, List<TopProductData> products, List<TopStoreData> stores) {
        long cancelled = sales.days().stream().mapToLong(DailySalesData.Day::cancelledOrders).sum();
        String bestDay = sales.days().stream()
                .filter(day -> day.sales() != null && day.sales().signum() > 0)
                .max((first, second) -> first.sales().compareTo(second.sales()))
                .map(day -> Formats.day(day.date()) + " (" + Formats.rupees(day.sales()) + ")")
                .orElse(null);
        return new SalesSummary(period, Formats.rupees(sales.totalSales()), sales.totalOrders(), cancelled, bestDay,
                products.stream().map(product -> new Seller(product.productName(), product.quantity(), Formats.rupees(product.sales()))).toList(),
                stores == null ? null
                        : stores.stream().map(store -> new Store(store.storeName(), store.orders(), Formats.rupees(store.sales()))).toList());
    }
}
