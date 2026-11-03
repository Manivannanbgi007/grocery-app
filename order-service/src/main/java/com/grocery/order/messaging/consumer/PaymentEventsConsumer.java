package com.grocery.order.messaging.consumer;

import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.payment.events and hands each event to the saga service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS)
public class PaymentEventsConsumer {

    private final OrderSagaService orderSagaService;

    @KafkaHandler
    public void onPaymentConfirmed(PaymentConfirmed event) {
        log.info("Received PaymentConfirmed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handlePaymentConfirmed(event);
    }

    @KafkaHandler
    public void onPaymentFailed(PaymentFailed event) {
        log.info("Received PaymentFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handlePaymentFailed(event);
    }

    @KafkaHandler
    public void onRefundCompleted(RefundCompleted event) {
        log.info("Received RefundCompleted eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleRefundCompleted(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
