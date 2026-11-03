package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.support.TestPaymentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected signatures were calculated outside Java (Python hmac + hashlib.sha256), so this test
 * checks our HMAC against an independent result.
 */
class RazorpaySignatureVerifierTest {

    private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier(TestPaymentProperties.razorpay());

    @Test
    void checkoutSignature_isHmacOfOrderIdAndPaymentId_withTheKeySecret() {
        String signature = "a5e4fdc7b6fcad5fa74ab0c93f18bd571b136f772f0a5abed071d900f7f41c7c";

        assertThat(verifier.isValidCheckoutSignature("order_IluGWxBm9U8zJ8", "pay_IluGZvc0Ts2Wa8", signature)).isTrue();
        assertThat(verifier.isValidCheckoutSignature("order_IluGWxBm9U8zJ8", "pay_SomebodyElse", signature)).isFalse();
    }

    @Test
    void webhookSignature_isHmacOfTheRawBody_withTheWebhookSecret() {
        String body = "{\"event\":\"payment.captured\"}";
        String signature = "149f0921b8dab66ba5504a4de4e376f5e30470d680899c30ffda46ef7391a360";

        assertThat(verifier.isValidWebhookSignature(body, signature)).isTrue();
        assertThat(verifier.isValidWebhookSignature(body + " ", signature)).isFalse();   // one changed byte is enough
        assertThat(verifier.isValidWebhookSignature(body, null)).isFalse();
    }
}
