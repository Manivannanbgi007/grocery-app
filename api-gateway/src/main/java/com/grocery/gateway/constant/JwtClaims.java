package com.grocery.gateway.constant;

/**
 * Custom claim names inside the JWT issued by auth-service (must match auth-service's JwtClaims).
 * The standard "sub" claim holds the user id.
 */
public final class JwtClaims {

    public static final String ROLE = "role";
    public static final String STORE_ID = "storeId";

    private JwtClaims() {
    }
}
