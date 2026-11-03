package com.grocery.notification.messaging.consumer;

import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.store.events and hands the events that need a notification to the service layer.
 * One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.STORE_EVENTS)
public class StoreEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onStoreRegistered(StoreRegistered event) {
        log.info("Received StoreRegistered eventId={} storeId={}", event.getEventId(), event.getStoreId());
        eventNotificationService.handleStoreRegistered(event);
    }

    @KafkaHandler
    public void onStoreApproved(StoreApproved event) {
        log.info("Received StoreApproved eventId={} storeId={}", event.getEventId(), event.getStoreId());
        eventNotificationService.handleStoreApproved(event);
    }

    @KafkaHandler
    public void onStoreStatusChanged(StoreStatusChanged event) {
        log.info("Received StoreStatusChanged eventId={} storeId={}", event.getEventId(), event.getStoreId());
        eventNotificationService.handleStoreStatusChanged(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
