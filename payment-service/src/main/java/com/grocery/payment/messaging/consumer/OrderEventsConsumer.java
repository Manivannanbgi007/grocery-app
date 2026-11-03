package com.grocery.payment.messaging.consumer;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderRejected;
import com.grocery.payment.constant.KafkaTopics;
import com.grocery.payment.service.PaymentSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events. All events of one order share a key (orderId), so they arrive in order:
 * OrderCreated always before OrderCancelled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final PaymentSagaService paymentSagaService;

    @KafkaHandler
    public void onOrderCreated(OrderCreated event) {
        log.info("Received OrderCreated eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleOrderCreated(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        log.info("Received OrderCancelled eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleOrderCancelled(event.getEventId(), "OrderCancelled", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderRejected(OrderRejected event) {
        log.info("Received OrderRejected eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleOrderCancelled(event.getEventId(), "OrderRejected", event.getOrderId());
    }

    @KafkaHandler
    public void onOrderDelivered(OrderDelivered event) {
        log.info("Received OrderDelivered eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleOrderDelivered(event);
    }

    /** Not delivered: same as a cancellation - refund if paid online, nothing to collect for COD. */
    @KafkaHandler
    public void onOrderDeliveryFailed(OrderDeliveryFailed event) {
        log.info("Received OrderDeliveryFailed eventId={} orderId={}", event.getEventId(), event.getOrderId());
        paymentSagaService.handleOrderCancelled(event.getEventId(), "OrderDeliveryFailed", event.getOrderId());
    }

    /** Order events payments do not need (OrderConfirmed, OrderAccepted, OrderPacked ...). */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
