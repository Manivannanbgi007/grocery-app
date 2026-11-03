package com.grocery.auth.social;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Apple's client secret: an ES256 JWT the shop signs itself, as Apple's documentation describes it. */
class AppleClientSecretTest {

    @Test
    void isAJwtSignedWithTheAppleKey_withTheClaimsAppleExpects() throws Exception {
        KeyPair key = TestProviders.newAppleKey();
        AppleClientSecret secret = new AppleClientSecret(TestProviders.apple(key).build(), Clock.systemUTC());

        Jws<Claims> jwt = Jwts.parser().verifyWith(key.getPublic()).build().parseSignedClaims(secret.value());

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(jwt.getHeader().getKeyId()).isEqualTo("KEY1234567");
        Claims claims = jwt.getPayload();
        assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
        assertThat(claims.getSubject()).isEqualTo("com.example.shop.web");
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void isReused_andRenewedTenMinutesBeforeItExpires() throws Exception {
        MovableClock clock = new MovableClock(Instant.now());
        AppleClientSecret secret = new AppleClientSecret(TestProviders.apple(TestProviders.newAppleKey()).build(), clock);

        String first = secret.value();
        clock.forward(Duration.ofMinutes(49));
        assertThat(secret.value()).isEqualTo(first);
        clock.forward(Duration.ofMinutes(2));
        assertThat(secret.value()).isNotEqualTo(first);
    }

    @Test
    void theKeyMayBeOneLineWithBackslashN_asInAnEnvironmentVariable() throws Exception {
        KeyPair key = TestProviders.newAppleKey();
        String oneLine = TestProviders.pem(key).replace("\n", "\\n");

        assertThat(AppleClientSecret.privateKey(oneLine)).isEqualTo(key.getPrivate());
    }

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void forward(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
