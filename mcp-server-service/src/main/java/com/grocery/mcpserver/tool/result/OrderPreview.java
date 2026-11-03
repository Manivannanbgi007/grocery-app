package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What prepareMyOrder returns: the summary of the order the customer's cart would become right now. NOTHING is
 * ordered: mcp-client-service shows this summary under the answer with a Confirm button, and only the customer's click
 * places the order. The amounts are cart-service's own (checked against today's catalog), as ₹ texts.
 *
 * @param deliverTo   "Home: Priya Sharma, 12, 5th Cross, Indiranagar, Bengaluru 560038"
 * @param payment     "Cash on delivery", or "Online (UPI or card), right after confirming"
 * @param recentOrder only when the customer placed an order a few minutes ago ("Order #196 was placed 3 minutes ago.")
 * @param confirm     what the Confirm button sends to order-service (not for the answer)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderPreview(
        String store,
        List<CartSummary.Line> items,
        String itemsTotal,
        String coupon,
        String couponDiscount,
        String deliveryFee,
        String payable,
        String deliverTo,
        String payment,
        String recentOrder,
        Confirm confirm) {

    /**
     * @param addressId           one of the customer's own addresses (order-service checks that it is theirs)
     * @param paymentMethod       COD or ONLINE
     * @param checkoutFingerprint order-service places the order only while the cart is still exactly this summary
     */
    public record Confirm(long addressId, String paymentMethod, String checkoutFingerprint) {
    }
}
