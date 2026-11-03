package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.config.PaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The check payment-service runs at start-up in razorpay mode. Razorpay's API is faked with MockRestServiceServer: no
 * network, and we can see whether the check called it.
 */
class RazorpaySettingsCheckTest {

    private static final String SECRET = "my_key_secret";
    private static final String WEBHOOK_SECRET = "my_webhook_secret";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer razorpay = MockRestServiceServer.bindTo(builder).build();

    @Test
    void testKeys_thatRazorpayAccepts_letTheServiceStart() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders?count=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3RfYWJjOm15X2tleV9zZWNyZXQ="))   // rzp_test_abc:my_key_secret
                .andRespond(withSuccess("{\"entity\":\"collection\",\"count\":0,\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> check(settings("test", "rzp_test_abc", SECRET, WEBHOOK_SECRET)).afterPropertiesSet())
                .doesNotThrowAnyException();
        razorpay.verify();
    }

    @Test
    void theRepositoryPlaceholders_stopTheService_andNameEveryVariable() {
        // application.yml's defaults: nobody set the environment variables
        RazorpaySettingsCheck check = check(settings("test", "rzp_test_replace_me", "replace_me", "replace_me"));

        assertThatThrownBy(check::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID is not set")
                .hasMessageContaining("RAZORPAY_KEY_SECRET is not set")
                .hasMessageContaining("RAZORPAY_WEBHOOK_SECRET is not set");
        razorpay.verify();      // Razorpay was not called
    }

    @Test
    void emptySettings_stopTheService() {
        assertThat(problemsOf("test", "", " ", null))
                .containsExactly("RAZORPAY_KEY_ID is not set", "RAZORPAY_KEY_SECRET is not set",
                        "RAZORPAY_WEBHOOK_SECRET is not set (without it anyone could send a fake payment webhook)");
    }

    @Test
    void aLiveKey_inTestMode_isRefused() {
        assertThat(problemsOf("test", "rzp_live_abc", SECRET, WEBHOOK_SECRET))
                .containsExactly("RAZORPAY_KEY_ID is not a test key (rzp_test_...), but RAZORPAY_MODE is test");
    }

    @Test
    void aTestKey_inLiveMode_isRefused() {
        assertThat(problemsOf("live", "rzp_test_abc", SECRET, WEBHOOK_SECRET))
                .containsExactly("RAZORPAY_KEY_ID is not a live key (rzp_live_...), but RAZORPAY_MODE is live");
    }

    @Test
    void anUnknownMode_isRefused() {
        assertThat(problemsOf("production", "rzp_live_abc", SECRET, WEBHOOK_SECRET))
                .containsExactly("RAZORPAY_MODE must be test or live");
    }

    @Test
    void liveKeys_inLiveMode_areFine() {
        assertThat(problemsOf("live", "rzp_live_abc", SECRET, WEBHOOK_SECRET)).isEmpty();
    }

    @Test
    void keysThatRazorpayRejects_stopTheService_withoutShowingTheSecret() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders?count=1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Authentication failed\"}}"));
        RazorpaySettingsCheck check = check(settings("test", "rzp_test_abc", SECRET, WEBHOOK_SECRET));

        assertThatThrownBy(check::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Razorpay did not accept RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET (401 Unauthorized)")
                .message().doesNotContain(SECRET, WEBHOOK_SECRET);
    }

    @Test
    void razorpayDown_onlyWarns_andTheServiceStarts() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders?count=1")).andRespond(withServiceUnavailable());

        assertThatCode(() -> check(settings("test", "rzp_test_abc", SECRET, WEBHOOK_SECRET)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void theProblems_neverShowTheValues() {
        assertThat(String.join("; ", problemsOf("live", "rzp_test_abc", "x_replace_me", "replace_me")))
                .doesNotContain("rzp_test_abc", "x_replace_me");
    }

    /** The check with the RestClient payment-service builds for these settings (RazorpayConfig), sending to the fake */
    private RazorpaySettingsCheck check(PaymentProperties properties) {
        return new RazorpaySettingsCheck(properties, new RazorpayConfig().razorpayRestClient(builder, properties));
    }

    /** Steps 1 and 2 only: the settings, without asking Razorpay */
    private static List<String> problemsOf(String mode, String keyId, String keySecret, String webhookSecret) {
        return RazorpaySettingsCheck.problemsOf(settings(mode, keyId, keySecret, webhookSecret).razorpay());
    }

    private static PaymentProperties settings(String mode, String keyId, String keySecret, String webhookSecret) {
        return new PaymentProperties("razorpay", Duration.ofMinutes(15), ZoneId.of("Asia/Kolkata"),
                new PaymentProperties.Razorpay(mode, "https://razorpay.test/v1", keyId, keySecret, webhookSecret));
    }
}
