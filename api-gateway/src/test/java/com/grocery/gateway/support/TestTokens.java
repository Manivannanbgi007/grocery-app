package com.grocery.gateway.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Creates JWTs exactly the way auth-service does (same library, same claims, HS256),
 * using the users from the seed data (docs/seed-data.md).
 */
public final class TestTokens {

    /** Same local default secret as application.yml (and auth-service). */
    private static final String SECRET = "local-dev-only-jwt-secret-change-me-0123456789";
    private static final String ISSUER = "grocery-auth-service";

    private TestTokens() {
    }

    public static String superAdmin() {
        return validToken("1", "SUPER_ADMIN", null);
    }

    public static String admin() {
        return validToken("2", "ADMIN", null);
    }

    public static String vendor() {
        return validToken("3", "VENDOR", 1L);
    }

    public static String customer() {
        return validToken("4", "CUSTOMER", null);
    }

    public static String deliveryPartner() {
        return validToken("5", "DELIVERY_PARTNER", null);
    }

    /** Expired one hour ago (more than the 60-second clock skew the gateway allows). */
    public static String expiredCustomer() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        return builder("4", "CUSTOMER", ISSUER, issuedAt, Instant.now().minus(Duration.ofHours(1)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    /** A forged token: correct format, but signed with a different secret. */
    public static String signedWithWrongSecret() {
        return builder("1", "SUPER_ADMIN", ISSUER, Instant.now(), Instant.now().plus(Duration.ofHours(1)))
                .signWith(key("some-other-secret-that-is-also-32-chars-long"), Jwts.SIG.HS256)
                .compact();
    }

    /** Correct secret, but not issued by auth-service. */
    public static String fromAnotherIssuer() {
        return builder("4", "CUSTOMER", "someone-else", Instant.now(), Instant.now().plus(Duration.ofHours(1)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    private static String validToken(String userId, String role, Long storeId) {
        JwtBuilder builder = builder(userId, role, ISSUER, Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        if (storeId != null) {
            builder.claim("storeId", storeId);
        }
        return builder.signWith(key(SECRET), Jwts.SIG.HS256).compact();
    }

    private static JwtBuilder builder(String userId, String role, String issuer, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
