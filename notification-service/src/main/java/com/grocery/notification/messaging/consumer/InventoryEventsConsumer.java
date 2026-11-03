package com.grocery.notification.messaging.consumer;

import com.grocery.events.inventory.LowStockDetected;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.inventory.events and hands the events that need a notification to the service layer.
 * One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS)
public class InventoryEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onLowStockDetected(LowStockDetected event) {
        log.info("Received LowStockDetected eventId={} productId={}", event.getEventId(), event.getProductId());
        eventNotificationService.handleLowStockDetected(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
