package com.grocery.payment.service.impl;

import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.payment.dto.request.RazorpayVerifyRequest;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.BusinessException;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.gateway.razorpay.RazorpaySignatureVerifier;
import com.grocery.payment.mapper.PaymentEventMapper;
import com.grocery.payment.mapper.PaymentMapper;
import com.grocery.payment.messaging.OutboxEventPublisher;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.OnlinePaymentRecorder;
import com.grocery.payment.service.ProcessedEventService;
import com.grocery.payment.support.TestPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProcessedEventService processedEventService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private PaymentGateway paymentGateway;

    private RazorpayPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        OnlinePaymentRecorder recorder = new OnlinePaymentRecorder(paymentGateway, new PaymentEventMapper(), outboxEventPublisher,
                TestPaymentProperties.FIXED_CLOCK);
        service = new RazorpayPaymentServiceImpl(paymentRepository, new PaymentMapper(), recorder,
                new RazorpaySignatureVerifier(TestPaymentProperties.razorpay()), processedEventService,
                TestPaymentProperties.razorpay(), JsonMapper.builder().build());
    }

    @Test
    void checkout_givesTheFrontendWhatRazorpayCheckoutNeeds() {
        when(paymentRepository.findByOrderIdAndCustomerUserId(20L, 4L)).thenReturn(Optional.of(awaitingPayment(20L)));

        var checkout = service.getCheckout(4L, 20L);

        assertThat(checkout.keyId()).isEqualTo("rzp_test_key");
        assertThat(checkout.razorpayOrderId()).isEqualTo("order_20");
        assertThat(checkout.amountInPaise()).isEqualTo(15800L);
    }

    @Test
    void verify_withTheRightSignature_marksThePaymentPaid() {
        Payment payment = awaitingPayment(21L);
        when(paymentRepository.findByOrderIdAndCustomerUserId(21L, 4L)).thenReturn(Optional.of(payment));
        String signature = hmac("order_21|pay_21", TestPaymentProperties.KEY_SECRET);

        service.verifyCheckout(4L, 21L, new RazorpayVerifyRequest("order_21", "pay_21", signature));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getGatewayPaymentId()).isEqualTo("pay_21");
        verify(outboxEventPublisher).publish(eq("payment"), eq(21L), any(PaymentConfirmed.class));
    }

    @Test
    void verify_withAWrongSignature_isRejected() {
        Payment payment = awaitingPayment(22L);
        when(paymentRepository.findByOrderIdAndCustomerUserId(22L, 4L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.verifyCheckout(4L, 22L, new RazorpayVerifyRequest("order_22", "pay_22", "forged")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The payment could not be verified");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void webhookCaptured_marksThePaymentPaid() {
        Payment payment = awaitingPayment(23L);
        when(processedEventService.markAsProcessed("evt_1", "razorpay.payment.captured")).thenReturn(true);
        when(paymentRepository.findByGatewayOrderId("order_23")).thenReturn(Optional.of(payment));
        String body = webhook("payment.captured", "pay_23", "order_23");

        String result = service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_1");

        assertThat(result).isEqualTo("Processed: payment.captured");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(outboxEventPublisher).publish(eq("payment"), eq(23L), any(PaymentConfirmed.class));
    }

    @Test
    void webhookCaptured_afterTheDeadlinePassed_refundsTheMoney() {
        Payment payment = awaitingPayment(24L);
        payment.markFailed("Not paid in time");
        when(processedEventService.markAsProcessed(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByGatewayOrderId("order_24")).thenReturn(Optional.of(payment));
        when(paymentGateway.refund(payment)).thenReturn("rfnd_24");
        String body = webhook("payment.captured", "pay_24", "order_24");

        service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_2");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReference()).isEqualTo("rfnd_24");
        verify(outboxEventPublisher).publish(eq("payment"), eq(24L), any(RefundCompleted.class));
    }

    @Test
    void webhookFailedAttempt_keepsThePaymentOpen() {
        Payment payment = awaitingPayment(25L);
        when(processedEventService.markAsProcessed(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByGatewayOrderId("order_25")).thenReturn(Optional.of(payment));
        String body = webhook("payment.failed", "pay_25", "order_25");

        service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_3");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined");
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void webhookCaptured_forAnotherAmount_doesNotMarkThePaymentPaid() {
        Payment payment = awaitingPayment(28L);
        when(processedEventService.markAsProcessed(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByGatewayOrderId("order_28")).thenReturn(Optional.of(payment));
        String body = webhook("payment.captured", "pay_28", "order_28", 100, "INR");     // ₹1 instead of ₹158

        String result = service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_6");

        assertThat(result).isEqualTo("Ignored: the amount or currency does not match the order");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void webhookCaptured_inAnotherCurrency_doesNotMarkThePaymentPaid() {
        Payment payment = awaitingPayment(29L);
        when(processedEventService.markAsProcessed(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByGatewayOrderId("order_29")).thenReturn(Optional.of(payment));
        String body = webhook("payment.captured", "pay_29", "order_29", 15800, "USD");

        String result = service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_7");

        assertThat(result).isEqualTo("Ignored: the amount or currency does not match the order");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void verifySentTwice_marksThePaymentPaidOnce() {
        Payment payment = awaitingPayment(30L);
        when(paymentRepository.findByOrderIdAndCustomerUserId(30L, 4L)).thenReturn(Optional.of(payment));
        RazorpayVerifyRequest request = new RazorpayVerifyRequest("order_30", "pay_30",
                hmac("order_30|pay_30", TestPaymentProperties.KEY_SECRET));

        service.verifyCheckout(4L, 30L, request);
        service.verifyCheckout(4L, 30L, request);     // a double click, or the browser trying again

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(outboxEventPublisher).publish(eq("payment"), eq(30L), any(PaymentConfirmed.class));   // only once
    }

    @Test
    void verify_withTheSignatureOfAnotherOrder_isRejected() {
        Payment payment = awaitingPayment(31L);
        when(paymentRepository.findByOrderIdAndCustomerUserId(31L, 4L)).thenReturn(Optional.of(payment));
        // A real signature, but of another (cheaper) order the customer paid: it must not pay this one
        String otherOrdersSignature = hmac("order_99|pay_99", TestPaymentProperties.KEY_SECRET);

        assertThatThrownBy(() -> service.verifyCheckout(4L, 31L, new RazorpayVerifyRequest("order_99", "pay_99", otherOrdersSignature)))
                .isInstanceOf(BusinessException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void webhookSentTwice_isHandledOnce() {
        when(processedEventService.markAsProcessed("evt_4", "razorpay.payment.captured")).thenReturn(false);
        String body = webhook("payment.captured", "pay_26", "order_26");

        String result = service.handleWebhook(body, hmac(body, TestPaymentProperties.WEBHOOK_SECRET), "evt_4");

        assertThat(result).isEqualTo("Ignored: already handled");
        verifyNoInteractions(paymentRepository, outboxEventPublisher);
    }

    @Test
    void webhookWithAWrongSignature_isRejected() {
        assertThatThrownBy(() -> service.handleWebhook(webhook("payment.captured", "pay_27", "order_27"), "forged", "evt_5"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid webhook signature");
        verifyNoInteractions(processedEventService, paymentRepository);
    }

    private Payment awaitingPayment(Long orderId) {
        Payment payment = Payment.forOrder(orderId, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.ONLINE);
        payment.setId(orderId + 100);
        payment.awaitOnlinePayment("order_" + orderId, TestPaymentProperties.NOW.plusSeconds(900));
        return payment;
    }

    /** A webhook for the payment's own amount: 158.00 rupees = 15800 paise */
    private String webhook(String event, String paymentId, String orderId) {
        return webhook(event, paymentId, orderId, 15800, "INR");
    }

    private String webhook(String event, String paymentId, String orderId, long amountInPaise, String currency) {
        return """
                {"entity":"event","event":"%s","contains":["payment"],
                 "payload":{"payment":{"entity":{"id":"%s","order_id":"%s","status":"%s","amount":%d,"currency":"%s",
                 "error_description":%s}}}}
                """.formatted(event, paymentId, orderId, event.equals("payment.captured") ? "captured" : "failed",
                amountInPaise, currency, event.equals("payment.failed") ? "\"Card declined\"" : "null");
    }

    /** Signs like Razorpay does (written separately from the class under test on purpose). */
    private String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
