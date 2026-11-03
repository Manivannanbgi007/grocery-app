package com.grocery.auth.service.impl;

import com.grocery.auth.config.JwtProperties;
import com.grocery.auth.constant.JwtClaims;
import com.grocery.auth.entity.User;
import com.grocery.auth.service.JwtService;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Builds HS256-signed JWTs.
 *
 * Example payload:
 * { "iss": "grocery-auth-service", "sub": "4", "email": "customer1@grocery.com",
 *   "name": "Priya Sharma", "role": "CUSTOMER", "iat": ..., "exp": ... }
 * Vendors also get "storeId".
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtServiceImpl(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        // The API gateway builds the same key from the same secret to verify tokens.
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.accessTokenLifetime());

        JwtBuilder builder = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(user.getId()))
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.NAME, user.getFullName())
                .claim(JwtClaims.ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));

        if (user.getStoreId() != null) {
            builder.claim(JwtClaims.STORE_ID, user.getStoreId());
        }

        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    @Override
    public long getExpirationSeconds() {
        return jwtProperties.accessTokenLifetime().toSeconds();
    }
}
