package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.tool.Formats;

import java.math.BigDecimal;
import java.util.List;

/**
 * What getMyOrderDetails returns: the status with its latest step, the lines, the amounts and the payment.
 * No delivery address and no phone numbers: the assistant does not need them.
 *
 * @param latestUpdate the newest step of the order's history, e.g. "Your items are packed (26 Oct 2026, 8:27 pm)"
 * @param cancelling   only when the customer can still cancel it (prepareMyOrderCancellation, or the website)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetails(
        long orderNumber,
        String store,
        String status,
        String reason,
        String latestUpdate,
        List<Line> items,
        String itemsTotal,
        String coupon,
        String couponDiscount,
        String deliveryFee,
        String total,
        String payment,
        String cancelling,
        String deliveryPartner,
        String placed,
        String delivered) {

    /**
     * @param unitPrice one pack
     * @param lineTotal all packs of this line (quantity x unitPrice)
     */
    public record Line(String name, String packSize, int quantity, String unitPrice, String lineTotal) {
    }

    public static OrderDetails from(OrderData order) {
        OrderData.Amounts amounts = order.amounts();
        return new OrderDetails(
                order.id(),
                order.storeName(),
                OrderTexts.status(order.status()),
                order.statusReason(),
                latestUpdate(order.timeline()),
                order.items().stream()
                        .map(item -> new Line(item.productName(), Formats.packSize(item.unit(), item.unitQuantity()),
                                item.quantity(), Formats.rupees(item.unitPrice()), Formats.rupees(item.lineTotal())))
                        .toList(),
                Formats.rupees(amounts.itemsTotal()),
                amounts.couponCode(),
                isZero(amounts.couponDiscount()) ? null : Formats.rupees(amounts.couponDiscount()),
                isZero(amounts.deliveryFee()) ? "Free" : Formats.rupees(amounts.deliveryFee()),
                Formats.rupees(amounts.totalAmount()),
                OrderTexts.payment(order.paymentMethod(), order.paymentStatus()),
                order.cancellable() ? "Can still be cancelled: ask here in the chat, or use My orders on the website" : null,
                order.deliveryPartnerName(),
                Formats.dateTime(order.placedAt()),
                Formats.dateTime(order.deliveredAt()));
    }

    private static String latestUpdate(List<OrderData.Step> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return null;
        }
        OrderData.Step latest = timeline.getLast();
        return latest.note() + " (" + Formats.dateTime(latest.at()) + ")";
    }

    private static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }
}
