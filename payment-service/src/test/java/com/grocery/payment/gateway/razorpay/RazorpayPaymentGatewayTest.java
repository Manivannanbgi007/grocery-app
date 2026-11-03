package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.exception.ExternalServiceException;
import com.grocery.payment.support.TestPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Razorpay's API is faked with MockRestServiceServer: no network, and we can check every request we send.
 */
class RazorpayPaymentGatewayTest {

    private MockRestServiceServer razorpay;
    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        razorpay = MockRestServiceServer.bindTo(builder).build();
        gateway = new RazorpayPaymentGateway(new RazorpayConfig().razorpayRestClient(builder, TestPaymentProperties.razorpay()));
    }

    @Test
    void createGatewayOrder_sendsTheAmountInPaise_withBasicAuth() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3Rfa2V5OnRlc3Rfa2V5X3NlY3JldA=="))  // rzp_test_key:test_key_secret
                .andExpect(jsonPath("$.amount").value(15800))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("order_10"))
                .andRespond(withSuccess("{\"id\":\"order_N5x9\",\"status\":\"created\",\"amount\":15800}", MediaType.APPLICATION_JSON));

        assertThat(gateway.createGatewayOrder(onlinePayment())).isEqualTo("order_N5x9");
        razorpay.verify();
    }

    @Test
    void refund_refundsTheCapturedPayment() {
        Payment payment = onlinePayment();
        payment.awaitOnlinePayment("order_N5x9", Instant.now().plusSeconds(900));
        payment.markPaid("pay_A1", Instant.now());
        razorpay.expect(requestTo("https://razorpay.test/v1/payments/pay_A1/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"amount\":15800}"))
                .andRespond(withSuccess("{\"id\":\"rfnd_B2\",\"status\":\"processed\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.refund(payment)).isEqualTo("rfnd_B2");
    }

    @Test
    void findCapturedPayment_ignoresFailedAttempts() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders/order_N5x9/payments"))
                .andRespond(withSuccess("""
                        {"count": 2, "items": [{"id": "pay_X", "status": "failed"}, {"id": "pay_Y", "status": "captured"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.findCapturedPayment("order_N5x9")).contains("pay_Y");
    }

    @Test
    void razorpayDown_becomesExternalServiceException() {
        razorpay.expect(requestTo("https://razorpay.test/v1/orders")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createGatewayOrder(onlinePayment()))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Razorpay could not open a payment. Please try again later.");
    }

    @Test
    void toPaise_handlesPaise() {
        assertThat(RazorpayPaymentGateway.toPaise(new BigDecimal("210.90"))).isEqualTo(21090L);
    }

    private Payment onlinePayment() {
        return Payment.forOrder(10L, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.ONLINE);
    }
}
