package com.grocery.order.messaging.consumer;

import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.inventory.events. Only StockReservationFailed matters to orders
 * (StockReserved is payment-service's signal; StockChanged is for search).
 *
 * One listener class per topic: each gets its own consumer thread, so a retry on one topic never
 * holds up the other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS)
public class InventoryEventsConsumer {

    private final OrderSagaService orderSagaService;

    @KafkaHandler
    public void onStockReservationFailed(StockReservationFailed event) {
        log.info("Received StockReservationFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleStockReservationFailed(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
