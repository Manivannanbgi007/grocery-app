package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;

/**
 * What prepareMyOrderCancellation returns: the order the customer wants to cancel, as they will see it under the
 * answer with a Confirm button. NOTHING is cancelled: only the customer's click calls order-service's cancel, which
 * checks again whose order it is and whether it can still be cancelled.
 *
 * @param status  "Packed by the store"
 * @param refund  what happens to the money: "The online payment of ₹138 will be refunded." or "Nothing was paid yet
 *                (cash on delivery)."
 * @param confirm what the Confirm button sends to order-service (not for the answer)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancellationPreview(
        long orderNumber,
        String store,
        String status,
        List<OrderDetails.Line> items,
        String total,
        String refund,
        Confirm confirm) {

    /** @param reason the customer's reason in their words, or null */
    public record Confirm(long orderId, String reason) {
    }

    public static CancellationPreview from(OrderData order, String reason) {
        String total = Formats.rupees(order.amounts().totalAmount());
        return new CancellationPreview(
                order.id(),
                order.storeName(),
                OrderTexts.status(order.status()),
                order.items().stream()
                        .map(item -> new OrderDetails.Line(item.productName(), Formats.packSize(item.unit(), item.unitQuantity()),
                                item.quantity(), Formats.rupees(item.unitPrice()), Formats.rupees(item.lineTotal())))
                        .toList(),
                total,
                refund(order, total),
                new Confirm(order.id(), reason));
    }

    /** Only money that was really paid comes back; cash on delivery was never paid before the delivery. */
    private static String refund(OrderData order, String total) {
        if ("PAID".equals(order.paymentStatus())) {
            return "The online payment of " + total + " will be refunded.";
        }
        return "COD".equals(order.paymentMethod()) ? "Nothing was paid yet (cash on delivery)." : "Nothing was paid yet.";
    }
}
