package com.grocery.inventory.service;

import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;

/**
 * Inventory's part of the order saga. Every method is idempotent: a duplicate event is skipped.
 */
public interface StockReservationService {

    /**
     * Holds the units of every item of the new order - all or nothing.
     * Publishes StockReserved, or StockReservationFailed (and then nothing is held).
     */
    void reserveForOrder(OrderCreated event);

    /** OrderCancelled / OrderRejected: the held units are available again (StockReleased). */
    void releaseForOrder(String eventId, String eventType, Long orderId);

    /** OrderDelivered: the held units left the store for good. */
    void commitForOrder(OrderDelivered event);
}
