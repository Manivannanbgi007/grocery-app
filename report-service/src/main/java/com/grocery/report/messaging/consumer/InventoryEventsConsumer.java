package com.grocery.report.messaging.consumer;

import com.grocery.events.inventory.StockChanged;
import com.grocery.report.constant.KafkaTopics;
import com.grocery.report.service.ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.inventory.events. One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS)
public class InventoryEventsConsumer {

    private final ReportProjectionService projectionService;

    @KafkaHandler
    public void onStockChanged(StockChanged event) {
        projectionService.onStockChanged(event);
    }

    /** Events of this topic the reports do not need. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
