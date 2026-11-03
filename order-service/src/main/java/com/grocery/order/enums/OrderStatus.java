package com.grocery.order.enums;

import java.util.Map;
import java.util.Set;

/**
 * The life of an order. Each status lists the statuses it may move to; anything else is refused.
 *
 *   PENDING ──► CONFIRMED ──► ACCEPTED ──► PACKED ──► READY_FOR_PICKUP ──► OUT_FOR_DELIVERY ──► DELIVERED
 *      │            │  └──► REJECTED (store)                                       └──► DELIVERY_FAILED
 *      └────────────┴──────────┴───────────┴──────────────┴──► CANCELLED (customer, admin or system)
 *
 *   PENDING           waiting for the stock reservation and the payment (saga running)
 *   CONFIRMED         stock reserved and payment settled: the store can accept it
 *   OUT_FOR_DELIVERY  the delivery partner picked it up; it can no longer be cancelled
 *   DELIVERY_FAILED   the partner could not deliver (customer unavailable, wrong address, damaged items)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    ACCEPTED,
    PACKED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_NEXT = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(ACCEPTED, REJECTED, CANCELLED),
            ACCEPTED, Set.of(PACKED, CANCELLED),
            PACKED, Set.of(READY_FOR_PICKUP, CANCELLED),
            READY_FOR_PICKUP, Set.of(OUT_FOR_DELIVERY, CANCELLED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED, DELIVERY_FAILED),
            DELIVERED, Set.of(),
            DELIVERY_FAILED, Set.of(),
            CANCELLED, Set.of(),
            REJECTED, Set.of());

    public boolean canMoveTo(OrderStatus next) {
        return ALLOWED_NEXT.get(this).contains(next);
    }

    /** "Cancel before dispatch": possible until the order leaves the store. */
    public boolean isCancellable() {
        return canMoveTo(CANCELLED);
    }
}
