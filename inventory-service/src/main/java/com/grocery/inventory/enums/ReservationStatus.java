package com.grocery.inventory.enums;

/**
 * What happened to the units held for an order.
 *
 *   RESERVED ──order cancelled or rejected──► RELEASED   (the units are available again)
 *       └─────order delivered───────────────► COMMITTED  (the units left the store for good)
 */
public enum ReservationStatus {
    RESERVED,
    RELEASED,
    COMMITTED
}
