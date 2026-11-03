package com.grocery.auth.constant;

/**
 * Names of the custom claims inside the JWT issued by auth-service.
 * The API gateway reads the same names to build the X-User-* headers.
 * The standard "sub" claim holds the user id.
 */
public final class JwtClaims {

    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String ROLE = "role";
    public static final String STORE_ID = "storeId";

    private JwtClaims() {
    }
}
