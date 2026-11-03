package com.grocery.store.enums;

/**
 * Store lifecycle:
 *
 * <pre>
 *  register ──► PENDING_APPROVAL ──approve──► ACTIVE ◄──enable── DISABLED
 *                     │    ▲                    │                   ▲
 *                  reject  │ vendor edits       └──────disable──────┘
 *                     ▼    │ the store
 *                  REJECTED
 * </pre>
 *
 * Only ACTIVE stores are shown to customers.
 */
public enum StoreStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    DISABLED
}
