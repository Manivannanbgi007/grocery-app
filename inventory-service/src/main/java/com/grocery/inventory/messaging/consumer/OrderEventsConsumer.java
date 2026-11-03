package com.grocery.inventory.messaging.consumer;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderRejected;
import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events (the order saga). All events of one order share a key (orderId),
 * so they arrive in order: OrderCreated (reserve) always before OrderCancelled (release).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final StockReservationService stockReservationService;

    @KafkaHandler
    public void onOrderCreated(OrderCreated event) {
        log.info("Received OrderCreated eventId={} orderId={}", event.getEventId(), event.getOrderId());
        stockReservationService.reserveForOrder(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled eventId={} orderId={}", event.getEventId(), event.getOrderId());
        stockReservationService.releaseForOrder(event.getEventId(), "OrderCancelled", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderRejected(OrderRejected event) {
        log.info("Received OrderRejected eventId={} orderId={}", event.getEventId(), event.getOrderId());
        stockReservationService.releaseForOrder(event.getEventId(), "OrderRejected", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderDelivered(OrderDelivered event) {
        log.info("Received OrderDelivered eventId={} orderId={}", event.getEventId(), event.getOrderId());
        stockReservationService.commitForOrder(event);
    }

    /** The partner brought the items back to the store: they can be sold again. */
    @KafkaHandler
    public void onOrderDeliveryFailed(OrderDeliveryFailed event) {
        log.info("Received OrderDeliveryFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        stockReservationService.releaseForOrder(event.getEventId(), "OrderDeliveryFailed", event.getOrderId());
    }

    /** Order events inventory does not need (OrderConfirmed, OrderAccepted ...). */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
