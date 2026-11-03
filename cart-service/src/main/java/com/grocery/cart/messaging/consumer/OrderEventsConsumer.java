package com.grocery.cart.messaging.consumer;

import com.grocery.cart.constant.KafkaTopics;
import com.grocery.cart.service.OrderEventService;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderRejected;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events and hands the events carts care about to the service layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final OrderEventService orderEventService;

    @KafkaHandler
    public void onOrderCreated(OrderCreated event) {
        log.info("Received OrderCreated eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderEventService.handleOrderCreated(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderEventService.releaseCouponOfOrder(event.getEventId(), "OrderCancelled", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderRejected(OrderRejected event) {
        log.info("Received OrderRejected eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderEventService.releaseCouponOfOrder(event.getEventId(), "OrderRejected", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderDeliveryFailed(OrderDeliveryFailed event) {
        log.info("Received OrderDeliveryFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        orderEventService.releaseCouponOfOrder(event.getEventId(), "OrderDeliveryFailed", event.getOrderId());
    }

    /** Order events carts do not need (OrderConfirmed, OrderAccepted ...). */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
