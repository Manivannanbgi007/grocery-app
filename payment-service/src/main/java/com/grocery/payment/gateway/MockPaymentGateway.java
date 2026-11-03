package com.grocery.payment.gateway;

import com.grocery.payment.entity.Payment;
import com.grocery.payment.logging.Loggable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A pretend payment provider: no network, no real money. Ids look like the real ones, so logs read the same.
 * The customer "pays" with POST /api/v1/payments/orders/{orderId}/mock-pay (MockPaymentController).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "mock", matchIfMissing = true)
@Loggable   // Stands in for Razorpay locally: LoggingAspect logs the time of every call and warns when it is slow
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String createGatewayOrder(Payment payment) {
        String gatewayOrderId = "mock_order_" + payment.getOrderId();
        log.info("[mock gateway] opened {} for {} (order id={})", gatewayOrderId, payment.getAmount(), payment.getOrderId());
        return gatewayOrderId;
    }

    @Override
    public String refund(Payment payment) {
        String refundId = "mock_refund_" + payment.getId();
        log.info("[mock gateway] refunded {} for order id={}: {}", payment.getAmount(), payment.getOrderId(), refundId);
        return refundId;
    }

    /** Mock payments are paid through MockPaymentController, never behind our back. */
    @Override
    public Optional<String> findCapturedPayment(String gatewayOrderId) {
        return Optional.empty();
    }
}
