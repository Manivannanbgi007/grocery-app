package com.grocery.auth.social;

import io.jsonwebtoken.Jwts;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple's client secret. Apple has no fixed secret: the shop signs a short JWT itself (ES256) with the key from Apple's
 * developer account. Issuer: the Team ID; subject: the client id (the Services ID); audience: https://appleid.apple.com;
 * header "kid": the Key ID. Apple accepts one for up to six months; this one lasts an hour and is renewed ten minutes
 * before it expires, so a copy of it is of little use.
 */
final class AppleClientSecret {

    static final String AUDIENCE = "https://appleid.apple.com";
    static final Duration LIFETIME = Duration.ofHours(1);
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(10);

    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey key;
    private final Clock clock;

    private String secret;
    private Instant renewAt = Instant.MIN;

    /** Checks the settings when the service starts: a missing or damaged key stops it with a clear message. */
    AppleClientSecret(SocialLoginProperties.Provider apple, Clock clock) {
        this.clientId = apple.clientId();
        this.teamId = required(apple.teamId(), "team-id (APPLE_TEAM_ID)");
        this.keyId = required(apple.keyId(), "key-id (APPLE_KEY_ID)");
        this.key = privateKey(required(apple.privateKey(), "private-key (APPLE_PRIVATE_KEY)"));
        this.clock = clock;
    }

    /** The current secret (a new one when it is about to expire). */
    synchronized String value() {
        Instant now = clock.instant();
        if (!now.isBefore(renewAt)) {
            secret = Jwts.builder()
                    .header().keyId(keyId).and()
                    .issuer(teamId)
                    .subject(clientId)
                    .audience().add(AUDIENCE).and()
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(LIFETIME)))
                    .signWith(key, Jwts.SIG.ES256)
                    .compact();
            renewAt = now.plus(LIFETIME).minus(RENEW_BEFORE);
        }
        return secret;
    }

    /**
     * The content of Apple's .p8 file ("-----BEGIN PRIVATE KEY-----", PKCS #8, an EC P-256 key). In an environment
     * variable the line breaks may also be written as \n.
     */
    static PrivateKey privateKey(String pem) {
        String base64 = pem.replace("\\n", "")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // The message never contains the key
            throw new IllegalStateException("app.social.providers.apple.private-key (APPLE_PRIVATE_KEY) is not the content "
                    + "of Apple's .p8 key file", ex);
        }
    }

    private static String required(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Sign in with Apple needs app.social.providers.apple." + setting);
        }
        return value.trim();
    }
}
