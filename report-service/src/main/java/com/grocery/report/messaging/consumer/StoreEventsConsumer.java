package com.grocery.report.messaging.consumer;

import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.report.constant.KafkaTopics;
import com.grocery.report.service.ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.store.events. One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.STORE_EVENTS)
public class StoreEventsConsumer {

    private final ReportProjectionService projectionService;

    @KafkaHandler
    public void onStoreRegistered(StoreRegistered event) {
        projectionService.onStoreRegistered(event);
    }

    @KafkaHandler
    public void onStoreApproved(StoreApproved event) {
        projectionService.onStoreApproved(event);
    }

    @KafkaHandler
    public void onStoreStatusChanged(StoreStatusChanged event) {
        projectionService.onStoreStatusChanged(event);
    }

    /** Events of this topic the reports do not need. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
