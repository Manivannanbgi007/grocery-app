package com.grocery.auth.enums;

/**
 * Platform roles. The hierarchy is:
 *
 * <pre>
 * SUPER_ADMIN
 * └── ADMIN
 *     ├── VENDOR
 *     ├── DELIVERY_PARTNER
 *     └── CUSTOMER
 * </pre>
 */
public enum Role {

    SUPER_ADMIN(false),
    ADMIN(false),
    VENDOR(true),
    DELIVERY_PARTNER(true),
    CUSTOMER(true);

    /** Whether a person can pick this role on the public registration page. */
    private final boolean selfRegistrationAllowed;

    Role(boolean selfRegistrationAllowed) {
        this.selfRegistrationAllowed = selfRegistrationAllowed;
    }

    public boolean isSelfRegistrationAllowed() {
        return selfRegistrationAllowed;
    }
}
