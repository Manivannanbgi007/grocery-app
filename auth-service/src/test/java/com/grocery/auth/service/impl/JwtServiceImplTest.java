package com.grocery.auth.service.impl;

import com.grocery.auth.config.JwtProperties;
import com.grocery.auth.constant.JwtClaims;
import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceImplTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    private final JwtServiceImpl jwtService =
            new JwtServiceImpl(new JwtProperties(SECRET, Duration.ofMinutes(15), "grocery-auth-service"), Clock.systemUTC());

    @Test
    void generateToken_containsUserClaims_andStoreIdForVendors() {
        User vendor = user(3L, Role.VENDOR, 1L);

        Claims claims = parse(jwtService.generateToken(vendor));

        assertThat(claims.getSubject()).isEqualTo("3");
        assertThat(claims.getIssuer()).isEqualTo("grocery-auth-service");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo("VENDOR");
        assertThat(claims.get(JwtClaims.EMAIL, String.class)).isEqualTo("vendor1@grocery.com");
        assertThat(((Number) claims.get(JwtClaims.STORE_ID)).longValue()).isEqualTo(1L);
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void generateToken_hasNoStoreId_forNonVendors() {
        Claims claims = parse(jwtService.generateToken(user(4L, Role.CUSTOMER, null)));

        assertThat(claims.containsKey(JwtClaims.STORE_ID)).isFalse();
    }

    @Test
    void expirationSeconds_matchesTheConfiguredLifetime() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(900L);   // 15 minutes
    }

    /** Verifies the signature with the shared secret - exactly what the API gateway does. */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private User user(Long id, Role role, Long storeId) {
        User user = new User();
        user.setId(id);
        user.setFullName("Ravi Kumar");
        user.setEmail("vendor1@grocery.com");
        user.setRole(role);
        user.setStoreId(storeId);
        return user;
    }
}
