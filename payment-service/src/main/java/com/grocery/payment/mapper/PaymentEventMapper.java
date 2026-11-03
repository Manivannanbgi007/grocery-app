package com.grocery.payment.mapper;

import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.payment.entity.Payment;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes. Amounts travel as decimal text ("158.00").
 */
@Component
public class PaymentEventMapper {

    public PaymentConfirmed toPaymentConfirmed(Payment payment) {
        return PaymentConfirmed.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setPaymentId(payment.getId())
                .setOrderId(payment.getOrderId())
                .setCustomerUserId(payment.getCustomerUserId())
                .setAmount(amount(payment))
                .setPaymentMethod(payment.getMethod().name())
                .build();
    }

    public PaymentFailed toPaymentFailed(Payment payment) {
        return PaymentFailed.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setPaymentId(payment.getId())
                .setOrderId(payment.getOrderId())
                .setCustomerUserId(payment.getCustomerUserId())
                .setReason(payment.getFailureReason())
                .build();
    }

    public RefundCompleted toRefundCompleted(Payment payment) {
        return RefundCompleted.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setPaymentId(payment.getId())
                .setOrderId(payment.getOrderId())
                .setCustomerUserId(payment.getCustomerUserId())
                .setAmount(amount(payment))
                .setRefundReference(payment.getRefundReference())
                .build();
    }

    private String amount(Payment payment) {
        return payment.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
