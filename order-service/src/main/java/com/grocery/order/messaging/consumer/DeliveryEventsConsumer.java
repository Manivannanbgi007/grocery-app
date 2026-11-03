package com.grocery.order.messaging.consumer;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.DeliveryCompleted;
import com.grocery.events.delivery.DeliveryFailed;
import com.grocery.events.delivery.DeliveryPickedUp;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.delivery.events: the delivery partner's actions move the order forward.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.DELIVERY_EVENTS)
public class DeliveryEventsConsumer {

    private final OrderSagaService orderSagaService;

    @KafkaHandler
    public void onDeliveryAssigned(DeliveryAssigned event) {
        log.info("Received DeliveryAssigned eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleDeliveryAssigned(event);
    }

    @KafkaHandler
    public void onDeliveryPickedUp(DeliveryPickedUp event) {
        log.info("Received DeliveryPickedUp eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleDeliveryPickedUp(event);
    }

    @KafkaHandler
    public void onDeliveryCompleted(DeliveryCompleted event) {
        log.info("Received DeliveryCompleted eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleDeliveryCompleted(event);
    }

    @KafkaHandler
    public void onDeliveryFailed(DeliveryFailed event) {
        log.info("Received DeliveryFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderSagaService.handleDeliveryFailed(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
