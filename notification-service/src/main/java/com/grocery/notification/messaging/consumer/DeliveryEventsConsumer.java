package com.grocery.notification.messaging.consumer;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.delivery.events. Pick-up and delivery already reach the customer through
 * order events (OrderOutForDelivery, OrderDelivered), so only DeliveryAssigned and the partner's
 * KYC decision need a notification here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.DELIVERY_EVENTS)
public class DeliveryEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onDeliveryAssigned(DeliveryAssigned event) {
        log.info("Received DeliveryAssigned eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleDeliveryAssigned(event);
    }

    @KafkaHandler
    public void onPartnerKycReviewed(PartnerKycReviewed event) {
        log.info("Received PartnerKycReviewed eventId={} partner={} {}", event.getEventId(), event.getPartnerUserId(), event.getStatus());
        eventNotificationService.handlePartnerKycReviewed(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
