package com.grocery.cart.dto.response;

import java.util.List;

/**
 * The cart checked against the CURRENT catalog, ready to become an order.
 * order-service places an order only when readyForCheckout is true.
 *
 * @param couponCode          the coupon whose discount is in the totals (null = none)
 * @param problems            what must be fixed first, e.g. "Pomegranate is out of stock" (empty when ready)
 * @param checkoutFingerprint changes with any product, quantity, price, coupon or total: an order placed from a summary
 *                            the customer saw sends it back, and order-service refuses the order when the cart changed
 */
public record CheckoutSummaryResponse(
        Long customerUserId,
        Long storeId,
        List<CartItemResponse> items,
        String couponCode,
        CartTotalsResponse totals,
        boolean readyForCheckout,
        List<String> problems,
        String checkoutFingerprint) {
}
