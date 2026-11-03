package com.grocery.payment.service;

import com.grocery.payment.constant.KafkaTopics;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.mapper.PaymentEventMapper;
import com.grocery.payment.messaging.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * What can happen to an online payment, each change together with its event. One place for it, because
 * several callers need it: the mock gateway, Razorpay's checkout and webhook, and the timeout job.
 *
 * Runs inside the caller's transaction (MANDATORY), so the change and its outbox event are saved together.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlinePaymentRecorder {

    private final PaymentGateway paymentGateway;
    private final PaymentEventMapper paymentEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    /** Paid -> PaymentConfirmed (order-service confirms the order). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paid(Payment payment, String gatewayPaymentId) {
        payment.markPaid(gatewayPaymentId, Instant.now(clock));
        publish(payment, paymentEventMapper.toPaymentConfirmed(payment));
        log.info("Order id={}: paid {} online ({})", payment.getOrderId(), payment.getAmount(), gatewayPaymentId);
    }

    /** Declined or not paid in time -> PaymentFailed (order-service cancels the order, the stock is released). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void failed(Payment payment, String reason) {
        payment.markFailed(reason);
        publish(payment, paymentEventMapper.toPaymentFailed(payment));
        log.info("Order id={}: online payment failed: {}", payment.getOrderId(), reason);
    }

    /**
     * The money arrived after the payment had failed or the order was cancelled: refund it at once
     * -> RefundCompleted (the customer is told).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundLatePayment(Payment payment, String gatewayPaymentId) {
        payment.acceptLatePayment(gatewayPaymentId, Instant.now(clock));
        String refundReference = paymentGateway.refund(payment);
        payment.markRefunded(refundReference, Instant.now(clock));
        publish(payment, paymentEventMapper.toRefundCompleted(payment));
        log.warn("Order id={}: {} arrived after the payment had ended - refunded ({})",
                payment.getOrderId(), payment.getAmount(), refundReference);
    }

    private void publish(Payment payment, SpecificRecordBase event) {
        outboxEventPublisher.publish(KafkaTopics.PAYMENT_AGGREGATE, payment.getOrderId(), event);
    }
}
