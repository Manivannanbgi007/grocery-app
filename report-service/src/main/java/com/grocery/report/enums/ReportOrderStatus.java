package com.grocery.report.enums;

/**
 * The order states reports care about (accepted, packed ... all count as CONFIRMED here).
 */
public enum ReportOrderStatus {
    PENDING,
    CONFIRMED,
    DELIVERED,
    CANCELLED,
    REJECTED,
    DELIVERY_FAILED
}
