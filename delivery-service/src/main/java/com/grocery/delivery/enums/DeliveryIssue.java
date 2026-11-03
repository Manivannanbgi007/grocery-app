package com.grocery.delivery.enums;

/**
 * Why a delivery could not be completed (reported by the partner).
 */
public enum DeliveryIssue {
    CUSTOMER_UNAVAILABLE,
    ADDRESS_WRONG,
    ITEM_DAMAGED,
    OTHER
}
