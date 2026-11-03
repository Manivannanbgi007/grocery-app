package com.grocery.notification.messaging.consumer;

import com.grocery.events.order.OrderAccepted;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderOutForDelivery;
import com.grocery.events.order.OrderPacked;
import com.grocery.events.order.OrderRejected;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events and hands the events that need a notification to the service layer.
 * One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onOrderConfirmed(OrderConfirmed event) {
        log.info("Received OrderConfirmed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderConfirmed(event);
    }

    @KafkaHandler
    public void onOrderAccepted(OrderAccepted event) {
        log.info("Received OrderAccepted eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderAccepted(event);
    }

    @KafkaHandler
    public void onOrderRejected(OrderRejected event) {
        log.info("Received OrderRejected eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderRejected(event);
    }

    @KafkaHandler
    public void onOrderPacked(OrderPacked event) {
        log.info("Received OrderPacked eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderPacked(event);
    }

    @KafkaHandler
    public void onOrderOutForDelivery(OrderOutForDelivery event) {
        log.info("Received OrderOutForDelivery eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderOutForDelivery(event);
    }

    @KafkaHandler
    public void onOrderDelivered(OrderDelivered event) {
        log.info("Received OrderDelivered eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderDelivered(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderCancelled(event);
    }

    @KafkaHandler
    public void onOrderDeliveryFailed(OrderDeliveryFailed event) {
        log.info("Received OrderDeliveryFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        eventNotificationService.handleOrderDeliveryFailed(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
