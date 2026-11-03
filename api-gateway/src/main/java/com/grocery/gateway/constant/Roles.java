package com.grocery.gateway.constant;

/**
 * Role names as they appear in the JWT "role" claim (same values as auth-service's Role enum).
 */
public final class Roles {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String VENDOR = "VENDOR";
    public static final String DELIVERY_PARTNER = "DELIVERY_PARTNER";
    public static final String CUSTOMER = "CUSTOMER";

    private Roles() {
    }
}
