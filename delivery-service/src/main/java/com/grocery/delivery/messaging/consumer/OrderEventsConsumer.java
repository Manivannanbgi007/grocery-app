package com.grocery.delivery.messaging.consumer;

import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.service.DeliverySagaService;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderReadyForPickup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events: an order ready for pickup becomes a delivery; a cancelled order cancels it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final DeliverySagaService deliverySagaService;

    @KafkaHandler
    public void onOrderReadyForPickup(OrderReadyForPickup event) {
        log.info("Received OrderReadyForPickup eventId={} orderId={}", event.getEventId(), event.getOrderId());
        deliverySagaService.handleOrderReadyForPickup(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled eventId={} orderId={}", event.getEventId(), event.getOrderId());
        deliverySagaService.handleOrderCancelled(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
