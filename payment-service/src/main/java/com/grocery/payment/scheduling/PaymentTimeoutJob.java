package com.grocery.payment.scheduling;

import com.grocery.payment.service.PaymentTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute (app.payment.timeout-check-interval): fail the online payments nobody paid in time.
 *
 * Each payment is handled in its own transaction, so one problem (e.g. Razorpay not answering) does not
 * stop the others; that payment is simply tried again at the next run.
 * With several instances of this service, two may pick the same payment: the second one then fails on the
 * row's @Version (optimistic locking) and changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutJob {

    private final PaymentTimeoutService paymentTimeoutService;

    @Scheduled(fixedDelayString = "${app.payment.timeout-check-interval}",
               initialDelayString = "${app.payment.timeout-check-interval}")
    public void failOverduePayments() {
        for (Long paymentId : paymentTimeoutService.findOverduePaymentIds()) {
            try {
                paymentTimeoutService.expire(paymentId);
            } catch (RuntimeException ex) {
                log.warn("Could not end overdue payment id={} - trying again at the next run: {}", paymentId, ex.getMessage());
            }
        }
    }
}
