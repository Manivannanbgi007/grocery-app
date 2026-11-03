package com.grocery.payment.service.impl;

import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.OnlinePaymentRecorder;
import com.grocery.payment.service.PaymentTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTimeoutServiceImpl implements PaymentTimeoutService {

    static final String NOT_PAID_IN_TIME = "Not paid in time";

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OnlinePaymentRecorder onlinePaymentRecorder;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverduePaymentIds() {
        return paymentRepository.findIdsWithDeadlineBefore(PaymentStatus.AWAITING_PAYMENT, Instant.now(clock));
    }

    @Override
    @Transactional
    public void expire(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (!payment.isOverdue(Instant.now(clock))) {
            return;   // paid (or cancelled) since the ids were read
        }
        Optional<String> capturedPayment = paymentGateway.findCapturedPayment(payment.getGatewayOrderId());
        if (capturedPayment.isPresent()) {
            log.warn("Order id={}: the gateway has a payment we were never told about - marking it paid", payment.getOrderId());
            onlinePaymentRecorder.paid(payment, capturedPayment.get());
            return;
        }
        onlinePaymentRecorder.failed(payment, NOT_PAID_IN_TIME);
    }
}
