package com.grocery.auth.messaging.consumer;

import com.grocery.auth.constant.KafkaTopics;
import com.grocery.auth.service.StoreEventService;
import com.grocery.events.store.StoreApproved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.store.events. Kept thin: it only hands each event to the service layer.
 * Spring calls the @KafkaHandler method whose parameter type matches the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.STORE_EVENTS)
public class StoreEventsConsumer {

    private final StoreEventService storeEventService;

    @KafkaHandler
    public void onStoreApproved(StoreApproved event) {
        log.info("Received StoreApproved eventId={} storeId={}", event.getEventId(), event.getStoreId());
        storeEventService.handleStoreApproved(event);
    }

    /** Store events auth-service does not need (StoreRegistered, StoreStatusChanged ...). */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
