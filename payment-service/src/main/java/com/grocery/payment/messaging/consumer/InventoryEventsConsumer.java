package com.grocery.payment.messaging.consumer;

import com.grocery.events.inventory.StockReserved;
import com.grocery.payment.constant.KafkaTopics;
import com.grocery.payment.service.PaymentSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.inventory.events; only StockReserved matters to payments.
 * A separate listener (own consumer thread), so retrying a StockReserved that arrived before its
 * OrderCreated never blocks the order topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.INVENTORY_EVENTS)
public class InventoryEventsConsumer {

    private final PaymentSagaService paymentSagaService;

    @KafkaHandler
    public void onStockReserved(StockReserved event) {
        log.info("Received StockReserved eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleStockReserved(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
