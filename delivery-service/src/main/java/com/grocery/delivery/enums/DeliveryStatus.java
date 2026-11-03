package com.grocery.delivery.enums;

import java.util.Map;
import java.util.Set;

/**
 * The life of a delivery (the delivery partner panel's status flow).
 *
 *   WAITING_FOR_PARTNER ──► ASSIGNED ──► ACCEPTED ──► PICKED_UP ──► OUT_FOR_DELIVERY ──► DELIVERED
 *        ▲                     │ partner rejects            │                │
 *        └─────────────────────┘                            └──► FAILED ◄────┘ ──► RETURNED (items back at the store)
 *   WAITING_FOR_PARTNER / ASSIGNED / ACCEPTED ──order cancelled──► CANCELLED
 */
public enum DeliveryStatus {
    WAITING_FOR_PARTNER,
    ASSIGNED,
    ACCEPTED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    RETURNED,
    CANCELLED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_NEXT = Map.of(
            WAITING_FOR_PARTNER, Set.of(ASSIGNED, CANCELLED),
            ASSIGNED, Set.of(ACCEPTED, WAITING_FOR_PARTNER, CANCELLED),
            ACCEPTED, Set.of(PICKED_UP, CANCELLED),
            PICKED_UP, Set.of(OUT_FOR_DELIVERY, FAILED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED, FAILED),
            DELIVERED, Set.of(),
            FAILED, Set.of(RETURNED),
            RETURNED, Set.of(),
            CANCELLED, Set.of());

    public boolean canMoveTo(DeliveryStatus next) {
        return ALLOWED_NEXT.get(this).contains(next);
    }

    /** The partner is busy with this delivery (they get one delivery at a time). */
    public boolean keepsPartnerBusy() {
        return this == ASSIGNED || this == ACCEPTED || this == PICKED_UP || this == OUT_FOR_DELIVERY || this == FAILED;
    }

    /**
     * Live tracking: the partner shares their location and the customer sees it on the map - from accepting the
     * delivery (on the way to the store) until handing it over. Not before (nobody has taken it yet).
     */
    public boolean isTracked() {
        return this == ACCEPTED || this == PICKED_UP || this == OUT_FOR_DELIVERY;
    }

    /** Nothing more to follow for the customer: the live tracking ends (and the stored location is deleted). */
    public boolean isOver() {
        return this == DELIVERED || this == FAILED || this == RETURNED || this == CANCELLED;
    }
}
