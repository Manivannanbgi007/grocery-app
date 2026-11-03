package com.grocery.report.messaging.consumer;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderRejected;
import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.constant.KafkaTopics;
import com.grocery.report.service.ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.order.events. One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.ORDER_EVENTS)
public class OrderEventsConsumer {

    private final ReportProjectionService projectionService;

    @KafkaHandler
    public void onOrderCreated(OrderCreated event) {
        projectionService.onOrderCreated(event);
    }

    @KafkaHandler
    public void onOrderConfirmed(OrderConfirmed event) {
        projectionService.onOrderConfirmed(event);
    }

    @KafkaHandler
    public void onOrderDelivered(OrderDelivered event) {
        projectionService.onOrderDelivered(event);
    }

    @KafkaHandler
    public void onOrderCancelled(OrderCancelled event) {
        projectionService.onOrderEnded(event.getEventId(), "OrderCancelled", event.getOrderId(),
                ReportOrderStatus.CANCELLED, event.getOccurredAt());
    }

    @KafkaHandler
    public void onOrderRejected(OrderRejected event) {
        projectionService.onOrderEnded(event.getEventId(), "OrderRejected", event.getOrderId(),
                ReportOrderStatus.REJECTED, event.getOccurredAt());
    }

    @KafkaHandler
    public void onOrderDeliveryFailed(OrderDeliveryFailed event) {
        projectionService.onOrderEnded(event.getEventId(), "OrderDeliveryFailed", event.getOrderId(),
                ReportOrderStatus.DELIVERY_FAILED, event.getOccurredAt());
    }

    /** Events of this topic the reports do not need. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
