package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the Razorpay settings once, when payment-service starts in razorpay mode (app.payment.gateway=razorpay).
 * Without it a wrong set-up shows only later: every online order fails to open its payment, and the customer waits on
 * "Reserving your items" until the order runs out of time.
 *
 * <pre>
 * 1. every setting is there, and none is still the placeholder of application.yml ("replace_me")
 * 2. the key id fits the mode: test mode takes rzp_test_ keys, live mode rzp_live_ keys (RAZORPAY_MODE, default test)
 * 3. Razorpay accepts the key id and secret: one read-only call, GET /orders?count=1
 * </pre>
 *
 * When 1, 2 or 3 fails, the service does not start, and the message names the environment variable to fix. When
 * Razorpay cannot be reached (no internet, an outage), only a warning is logged: the keys may well be right, and
 * opening a payment is retried later. The value of a setting is never logged.
 *
 * It runs while the beans are created (InitializingBean), so before the Kafka listeners start: a service that cannot
 * take payments never picks up an order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
public class RazorpaySettingsCheck implements InitializingBean {

    static final String TEST = "test";
    static final String LIVE = "live";
    /** The placeholder of application.yml: rzp_test_replace_me, replace_me */
    private static final String PLACEHOLDER = "replace_me";

    private final PaymentProperties paymentProperties;
    private final RestClient razorpayRestClient;

    @Override
    public void afterPropertiesSet() {
        PaymentProperties.Razorpay razorpay = paymentProperties.razorpay();
        List<String> problems = problemsOf(razorpay);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Razorpay is not set up: " + String.join("; ", problems) + ".");
        }
        checkThatRazorpayAcceptsTheKeys(razorpay.mode());
    }

    /** Steps 1 and 2: what is wrong with the settings, each problem naming its environment variable; empty when nothing */
    static List<String> problemsOf(PaymentProperties.Razorpay razorpay) {
        List<String> problems = new ArrayList<>();
        String mode = razorpay.mode();
        if (!TEST.equals(mode) && !LIVE.equals(mode)) {
            problems.add("RAZORPAY_MODE must be test or live");
        }
        if (notSet(razorpay.keyId())) {
            problems.add("RAZORPAY_KEY_ID is not set");
        } else if (TEST.equals(mode) && !razorpay.keyId().startsWith("rzp_test_")) {
            problems.add("RAZORPAY_KEY_ID is not a test key (rzp_test_...), but RAZORPAY_MODE is test");
        } else if (LIVE.equals(mode) && !razorpay.keyId().startsWith("rzp_live_")) {
            problems.add("RAZORPAY_KEY_ID is not a live key (rzp_live_...), but RAZORPAY_MODE is live");
        }
        if (notSet(razorpay.keySecret())) {
            problems.add("RAZORPAY_KEY_SECRET is not set");
        }
        if (notSet(razorpay.webhookSecret())) {
            // The webhook URL is public: with the placeholder anyone could sign a fake "payment.captured"
            problems.add("RAZORPAY_WEBHOOK_SECRET is not set (without it anyone could send a fake payment webhook)");
        }
        return problems;
    }

    /** Step 3. The RestClient sends the key id and secret as Basic auth (RazorpayConfig). */
    private void checkThatRazorpayAcceptsTheKeys(String mode) {
        try {
            razorpayRestClient.get().uri("/orders?count=1").retrieve().toBodilessEntity();
            log.info("Razorpay {} mode: Razorpay accepted the key id and secret", mode);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new IllegalStateException("Razorpay did not accept RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET (401 "
                    + "Unauthorized). Copy both from the same Razorpay account and mode: Dashboard > Account & Settings "
                    + "> API Keys.", ex);
        } catch (RestClientException ex) {
            log.warn("Razorpay could not be reached to check the keys ({}). The service starts anyway; opening a payment "
                    + "is retried.", ex.getClass().getSimpleName());
        }
    }

    private static boolean notSet(String value) {
        return value == null || value.isBlank() || value.contains(PLACEHOLDER);
    }
}
