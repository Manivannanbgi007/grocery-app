package com.grocery.payment.service.impl;

import com.grocery.events.inventory.StockReserved;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.payment.config.PaymentProperties;
import com.grocery.payment.constant.KafkaTopics;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.mapper.PaymentEventMapper;
import com.grocery.payment.messaging.OutboxEventPublisher;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.PaymentSagaService;
import com.grocery.payment.service.ProcessedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaServiceImpl implements PaymentSagaService {

    private final PaymentRepository paymentRepository;
    private final ProcessedEventService processedEventService;
    private final PaymentGateway paymentGateway;
    private final PaymentEventMapper paymentEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void handleOrderCreated(OrderCreated event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderCreated")
                || paymentRepository.existsByOrderId(event.getOrderId())) {
            return;
        }
        Payment payment = Payment.forOrder(event.getOrderId(), event.getCustomerUserId(), event.getStoreId(),
                new BigDecimal(event.getTotalAmount()), PaymentMethod.valueOf(event.getPaymentMethod()));
        paymentRepository.save(payment);
        log.info("Payment created for order id={}: {} {}", payment.getOrderId(), payment.getAmount(), payment.getMethod());
    }

    @Override
    @Transactional
    public void handleStockReserved(StockReserved event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "StockReserved")) {
            return;
        }
        // StockReserved comes on another topic than OrderCreated, so it can arrive first. Throwing here
        // rolls everything back and the error handler retries a few seconds later, when the payment exists.
        Payment payment = paymentRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment for order " + event.getOrderId() + " yet (OrderCreated not handled yet) - will retry"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("StockReserved for order id={} whose payment is {} - ignored", payment.getOrderId(), payment.getStatus());
            return;
        }

        if (payment.getMethod() == PaymentMethod.COD) {
            payment.acceptCashOnDelivery();
            outboxEventPublisher.publish(KafkaTopics.PAYMENT_AGGREGATE, payment.getOrderId(), paymentEventMapper.toPaymentConfirmed(payment));
            log.info("Order id={}: cash on delivery accepted", payment.getOrderId());
        } else {
            Instant payBefore = Instant.now(clock).plus(paymentProperties.onlinePaymentTimeout());
            payment.awaitOnlinePayment(paymentGateway.createGatewayOrder(payment), payBefore);
            log.info("Order id={}: waiting for the customer to pay {} online before {}", payment.getOrderId(), payment.getAmount(), payBefore);
        }
    }

    @Override
    @Transactional
    public void handleOrderCancelled(String eventId, String eventType, Long orderId) {
        if (!processedEventService.markAsProcessed(eventId, eventType)) {
            return;
        }
        Optional<Payment> found = paymentRepository.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("{} for order id={} without a payment - nothing to do", eventType, orderId);
            return;
        }
        Payment payment = found.get();

        if (payment.needsRefund()) {
            String refundReference = paymentGateway.refund(payment);
            payment.markRefunded(refundReference, Instant.now());
            outboxEventPublisher.publish(KafkaTopics.PAYMENT_AGGREGATE, orderId, paymentEventMapper.toRefundCompleted(payment));
            log.info("Order id={} {}: refunded {} ({})", orderId, eventType, payment.getAmount(), refundReference);
        } else if (payment.canBeCancelledWithoutRefund()) {
            payment.cancel();
            log.info("Order id={} {}: payment cancelled, nothing was paid", orderId, eventType);
        } else {
            log.info("Order id={} {}: payment is already {} - nothing to do", orderId, eventType, payment.getStatus());
        }
    }

    @Override
    @Transactional
    public void handleOrderDelivered(OrderDelivered event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderDelivered")) {
            return;
        }
        paymentRepository.findByOrderId(event.getOrderId())
                .filter(payment -> payment.getStatus() == PaymentStatus.CASH_ON_DELIVERY)
                .ifPresent(payment -> {
                    payment.markCollected(Instant.now());
                    log.info("Order id={}: cash {} collected", payment.getOrderId(), payment.getAmount());
                });
    }
}
