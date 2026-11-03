package com.grocery.order.enums;

/**
 * Who changed the status of an order (shown in the tracking timeline).
 */
public enum ChangedBy {
    CUSTOMER,
    VENDOR,
    ADMIN,
    DELIVERY_PARTNER,
    /** The saga, e.g. the order was cancelled because the stock ran out. */
    SYSTEM
}
