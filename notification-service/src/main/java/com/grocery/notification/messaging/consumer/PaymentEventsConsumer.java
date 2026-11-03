package com.grocery.notification.messaging.consumer;

import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.payment.events and hands the events that need a notification to the service layer.
 * One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS)
public class PaymentEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onPaymentFailed(PaymentFailed event) {
        log.info("Received PaymentFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handlePaymentFailed(event);
    }

    @KafkaHandler
    public void onRefundCompleted(RefundCompleted event) {
        log.info("Received RefundCompleted eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleRefundCompleted(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
