package com.grocery.cart.service;

import com.grocery.events.order.OrderCreated;

/**
 * cart-service's part of the order saga. Every method is idempotent: a duplicate event is skipped.
 */
public interface OrderEventService {

    /** Removes the ordered products from the cart and counts the coupon use. */
    void handleOrderCreated(OrderCreated event);

    /**
     * The order ended without a delivery (cancelled, rejected by the store, delivery failed):
     * its coupon use no longer counts (e.g. WELCOME50 can be used again).
     */
    void releaseCouponOfOrder(String eventId, String eventType, Long orderId);
}
