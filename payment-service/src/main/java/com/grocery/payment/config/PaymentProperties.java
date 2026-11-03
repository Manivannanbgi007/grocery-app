package com.grocery.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Settings under "app.payment" in application.yml.
 *
 * @param gateway              mock (no real money) or razorpay
 * @param onlinePaymentTimeout an online payment that is not paid within this time fails, and the order is cancelled
 * @param timeZone             the day boundaries of the admin payment summary
 * @param razorpay             the Razorpay account (only used when gateway = razorpay)
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        @NotBlank String gateway,
        @NotNull Duration onlinePaymentTimeout,
        @NotNull ZoneId timeZone,
        @Valid @NotNull Razorpay razorpay) {

    /**
     * @param mode          test or live: test mode takes only rzp_test_ keys, live mode only rzp_live_ keys
     *                      (RazorpaySettingsCheck), so test and live keys are never mixed up
     * @param baseUrl       https://api.razorpay.com/v1
     * @param keyId         public key id (test mode keys start with rzp_test_); the browser needs it for Razorpay Checkout
     * @param keySecret     secret key: API calls and the checkout signature
     * @param webhookSecret the secret entered when the webhook was created in the Razorpay dashboard
     */
    public record Razorpay(String mode, String baseUrl, String keyId, String keySecret, String webhookSecret) {
    }
}
