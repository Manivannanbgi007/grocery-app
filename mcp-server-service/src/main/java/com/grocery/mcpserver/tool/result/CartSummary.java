package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.tool.Formats;

import java.math.BigDecimal;
import java.util.List;

/**
 * What getMyCart returns: the cart's lines and the totals exactly as cart-service calculated them.
 *
 * @param minimumOrder the minimum order and whether the cart reaches it, both ways: after "the minimum order is ₹99;
 *                     this cart does not reach it yet", the model repeated that from memory when a change made the cart
 *                     reach it, and the answer check then had to reject it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartSummary(
        String store,
        List<Line> items,
        String itemsTotal,
        String savings,
        String coupon,
        String couponDiscount,
        String couponMessage,
        String deliveryFee,
        String payable,
        String minimumOrder) {

    /**
     * Both prices, named plainly: with only one "price" the model took the line total for the price of one pack.
     *
     * @param unitPrice one pack
     * @param lineTotal all packs of this line (quantity x unitPrice)
     */
    public record Line(Long productId, String name, String packSize, int quantity, String unitPrice, String lineTotal) {
    }

    /** One line of a cart (or of its checkout), prices as ₹ texts */
    public static Line line(CartData.Item item) {
        return new Line(item.productId(), item.productName(), Formats.packSize(item.unit(), item.unitQuantity()),
                item.quantity(), Formats.rupees(item.sellingPrice()), Formats.rupees(item.lineTotal()));
    }

    public static CartSummary from(CartData cart, String storeName) {
        CartData.Totals totals = cart.totals();
        return new CartSummary(
                storeName,
                cart.items().stream().map(CartSummary::line).toList(),
                Formats.rupees(totals.itemsTotal()),
                isZero(totals.productSavings()) ? null : Formats.rupees(totals.productSavings()),
                cart.couponCode(),
                isZero(totals.couponDiscount()) ? null : Formats.rupees(totals.couponDiscount()),
                cart.couponMessage(),
                isZero(totals.deliveryFee()) ? "Free" : Formats.rupees(totals.deliveryFee()),
                Formats.rupees(totals.payableAmount()),
                minimumOrder(totals));
    }

    /** "The minimum order is ₹99; this cart reaches it." (null when the shop has no minimum) */
    private static String minimumOrder(CartData.Totals totals) {
        if (totals.minimumOrderValue() == null || totals.minimumOrderValue().signum() == 0) {
            return null;
        }
        return "The minimum order is " + Formats.rupees(totals.minimumOrderValue())
                + (totals.minimumOrderMet() ? "; this cart reaches it." : "; this cart does not reach it yet.");
    }

    private static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }
}
