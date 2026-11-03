package com.grocery.report.enums;

public enum ReportPaymentStatus {
    PENDING,
    /** Paid online (or COD cash collected at delivery). */
    PAID,
    /** COD accepted, cash not collected yet. */
    CASH_ON_DELIVERY,
    REFUNDED
}
