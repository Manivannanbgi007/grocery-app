package com.grocery.delivery.enums;

/**
 * The delivery partner's verification (KYC). Only APPROVED partners can go online and get deliveries.
 *
 *   NOT_SUBMITTED ──submit──► PENDING ──admin──► APPROVED
 *                               ▲      └─admin─► REJECTED ──submit again──┘
 */
public enum KycStatus {
    NOT_SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED
}
