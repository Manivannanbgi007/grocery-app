package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks that a message really comes from Razorpay. Razorpay signs with HMAC-SHA256 (hex):
 *
 *   after Checkout:  signature = HMAC(key secret,     razorpay_order_id + "|" + razorpay_payment_id)
 *   webhooks:        signature = HMAC(webhook secret, the raw request body)   in header X-Razorpay-Signature
 *
 * Without this check anybody could tell us "order X is paid".
 */
@Component
@RequiredArgsConstructor
public class RazorpaySignatureVerifier {

    private final PaymentProperties paymentProperties;

    public boolean isValidCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        return matches(gatewayOrderId + "|" + gatewayPaymentId, paymentProperties.razorpay().keySecret(), signature);
    }

    public boolean isValidWebhookSignature(String rawBody, String signature) {
        return matches(rawBody, paymentProperties.razorpay().webhookSecret(), signature);
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    /** MessageDigest.isEqual takes the same time for every input, so the signature cannot be guessed byte by byte. */
    private boolean matches(String data, String secret, String signature) {
        if (data == null || secret == null || signature == null) {
            return false;
        }
        byte[] expected = hmacSha256Hex(data, secret).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}
