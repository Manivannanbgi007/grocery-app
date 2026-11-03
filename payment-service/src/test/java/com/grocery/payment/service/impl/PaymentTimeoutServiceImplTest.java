package com.grocery.payment.service.impl;

import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.mapper.PaymentEventMapper;
import com.grocery.payment.messaging.OutboxEventPublisher;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.OnlinePaymentRecorder;
import com.grocery.payment.support.TestPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private PaymentTimeoutServiceImpl timeoutService;

    @BeforeEach
    void setUp() {
        OnlinePaymentRecorder recorder = new OnlinePaymentRecorder(paymentGateway, new PaymentEventMapper(), outboxEventPublisher,
                TestPaymentProperties.FIXED_CLOCK);
        timeoutService = new PaymentTimeoutServiceImpl(paymentRepository, paymentGateway, recorder, TestPaymentProperties.FIXED_CLOCK);
    }

    @Test
    void notPaidByTheDeadline_fails_soTheOrderIsCancelled() {
        Payment payment = awaitingPayment(30L, TestPaymentProperties.NOW.minusSeconds(1));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findCapturedPayment("order_30")).thenReturn(Optional.empty());

        timeoutService.expire(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Not paid in time");
        verify(outboxEventPublisher).publish(eq("payment"), eq(30L), any(PaymentFailed.class));
    }

    @Test
    void paidButTheMessageGotLost_isMarkedPaid() {
        Payment payment = awaitingPayment(31L, TestPaymentProperties.NOW.minusSeconds(1));
        when(paymentRepository.findById(2L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findCapturedPayment("order_31")).thenReturn(Optional.of("pay_31"));

        timeoutService.expire(2L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(outboxEventPublisher).publish(eq("payment"), eq(31L), any(PaymentConfirmed.class));
    }

    @Test
    void paidJustBeforeTheJobGotToIt_isLeftAlone() {
        Payment payment = awaitingPayment(32L, TestPaymentProperties.NOW.minusSeconds(1));
        payment.markPaid("pay_32", Instant.now());
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        timeoutService.expire(3L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(paymentGateway, outboxEventPublisher);
    }

    private Payment awaitingPayment(Long orderId, Instant payBefore) {
        Payment payment = Payment.forOrder(orderId, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.ONLINE);
        payment.setId(orderId + 100);
        payment.awaitOnlinePayment("order_" + orderId, payBefore);
        return payment;
    }
}
