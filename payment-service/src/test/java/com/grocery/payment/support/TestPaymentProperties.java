package com.grocery.payment.support;

import com.grocery.payment.config.PaymentProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Settings and a fixed clock for plain unit tests (no Spring context).
 */
public final class TestPaymentProperties {

    public static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String KEY_SECRET = "test_key_secret";
    public static final String WEBHOOK_SECRET = "test_webhook_secret";

    private TestPaymentProperties() {
    }

    public static PaymentProperties razorpay() {
        return new PaymentProperties("razorpay", Duration.ofMinutes(15), ZoneId.of("Asia/Kolkata"),
                new PaymentProperties.Razorpay("test", "https://razorpay.test/v1", "rzp_test_key", KEY_SECRET, WEBHOOK_SECRET));
    }
}
