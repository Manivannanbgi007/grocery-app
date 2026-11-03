package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.tool.Formats;

/**
 * One order in the list of getMyOrders.
 *
 * @param orderNumber the number the customer sees on the website ("Order #36"); getMyOrderDetails needs it
 */
public record OrderOverview(long orderNumber, String store, String status, int items, String total, String placed) {

    public static OrderOverview from(OrderSummaryData order) {
        return new OrderOverview(order.id(), order.storeName(), OrderTexts.status(order.status()), order.itemCount(),
                Formats.rupees(order.totalAmount()), Formats.dateTime(order.placedAt()));
    }
}
