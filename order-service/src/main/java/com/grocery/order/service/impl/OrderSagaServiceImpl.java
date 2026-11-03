package com.grocery.order.service.impl;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.DeliveryCompleted;
import com.grocery.events.delivery.DeliveryFailed;
import com.grocery.events.delivery.DeliveryPickedUp;
import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.inventory.UnavailableItem;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.entity.Order;
import com.grocery.order.entity.OrderItem;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.ResourceNotFoundException;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.OrderCanceller;
import com.grocery.order.service.OrderSagaService;
import com.grocery.order.service.ProcessedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every handler starts the same way: skip a duplicate event (processed_event table), then load the order.
 * An event that no longer fits the order's status is logged and ignored, never an error:
 * e.g. PaymentConfirmed for an order the customer already cancelled (payment-service refunds it on OrderCancelled).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaServiceImpl implements OrderSagaService {

    private final OrderRepository orderRepository;
    private final ProcessedEventService processedEventService;
    private final OrderEventMapper orderEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OrderCanceller orderCanceller;

    @Override
    @Transactional
    public void handleStockReservationFailed(StockReservationFailed event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "StockReservationFailed")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        if (!order.getStatus().canMoveTo(OrderStatus.CANCELLED)) {
            log.warn("StockReservationFailed for order id={} which is {} - ignored", order.getId(), order.getStatus());
            return;
        }
        orderCanceller.cancel(order, "Not enough stock: " + describe(order, event), ChangedBy.SYSTEM);
    }

    @Override
    @Transactional
    public void handlePaymentConfirmed(PaymentConfirmed event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "PaymentConfirmed")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("PaymentConfirmed for order id={} which is {} - ignored", order.getId(), order.getStatus());
            return;
        }
        order.confirm(order.getPaymentMethod() == PaymentMethod.COD ? OrderPaymentStatus.CASH_ON_DELIVERY : OrderPaymentStatus.PAID,
                event.getPaymentId());   // the invoice names the payment
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), orderEventMapper.toOrderConfirmed(order));
        log.info("Order id={} CONFIRMED ({})", order.getId(), order.getPaymentStatus());
    }

    @Override
    @Transactional
    public void handlePaymentFailed(PaymentFailed event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "PaymentFailed")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        order.markPaymentFailed();
        if (order.getStatus().canMoveTo(OrderStatus.CANCELLED)) {
            orderCanceller.cancel(order, "Payment failed: " + event.getReason(), ChangedBy.SYSTEM);
        }
    }

    @Override
    @Transactional
    public void handleRefundCompleted(RefundCompleted event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "RefundCompleted")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        order.markRefunded();
        log.info("Order id={} refunded: {} ({})", order.getId(), event.getAmount(), event.getRefundReference());
    }

    @Override
    @Transactional
    public void handleDeliveryAssigned(DeliveryAssigned event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "DeliveryAssigned")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        order.assignDeliveryPartner(event.getPartnerName(), event.getPartnerPhone());
        log.info("Order id={}: delivery partner {} assigned", order.getId(), event.getPartnerName());
    }

    @Override
    @Transactional
    public void handleDeliveryPickedUp(DeliveryPickedUp event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "DeliveryPickedUp")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        if (!order.getStatus().canMoveTo(OrderStatus.OUT_FOR_DELIVERY)) {
            log.warn("DeliveryPickedUp for order id={} which is {} - ignored", order.getId(), order.getStatus());
            return;
        }
        order.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), orderEventMapper.toOrderOutForDelivery(order));
    }

    @Override
    @Transactional
    public void handleDeliveryCompleted(DeliveryCompleted event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "DeliveryCompleted")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        if (!order.getStatus().canMoveTo(OrderStatus.DELIVERED)) {
            log.warn("DeliveryCompleted for order id={} which is {} - ignored", order.getId(), order.getStatus());
            return;
        }
        order.markDelivered(ChangedBy.DELIVERY_PARTNER, event.getOccurredAt());
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), orderEventMapper.toOrderDelivered(order));
        log.info("Order id={} DELIVERED", order.getId());
    }

    @Override
    @Transactional
    public void handleDeliveryFailed(DeliveryFailed event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "DeliveryFailed")) {
            return;
        }
        Order order = findOrder(event.getOrderId());
        if (!order.getStatus().canMoveTo(OrderStatus.DELIVERY_FAILED)) {
            log.warn("DeliveryFailed for order id={} which is {} - ignored", order.getId(), order.getStatus());
            return;
        }
        String reason = "Delivery failed: " + event.getIssue().replace('_', ' ').toLowerCase()
                + (event.getNote() == null ? "" : " (" + event.getNote() + ")");
        order.failDelivery(reason);
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), orderEventMapper.toOrderDeliveryFailed(order));
        log.info("Order id={} DELIVERY_FAILED: {}", order.getId(), reason);
    }

    /**
     * The order exists as soon as its OrderCreated event exists, so a missing order is a real error:
     * the event is retried and then goes to the dead-letter topic.
     */
    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    /** "Amul Butter (3 left), Tomato (sold out)" - the inventory event only has product ids, the order has the names. */
    private String describe(Order order, StockReservationFailed event) {
        Map<Long, String> names = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getProductName, (first, second) -> first));
        return event.getItems().stream()
                .map(item -> names.getOrDefault(item.getProductId(), "product " + item.getProductId()) + availability(item))
                .collect(Collectors.joining(", "));
    }

    private String availability(UnavailableItem item) {
        return item.getAvailableQuantity() == 0 ? " (sold out)" : " (" + item.getAvailableQuantity() + " left)";
    }
}
