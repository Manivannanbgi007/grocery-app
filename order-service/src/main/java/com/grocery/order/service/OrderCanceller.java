package com.grocery.order.service;

import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels an order and publishes OrderCancelled. Shared by the customer, admin and saga services,
 * because the compensation (release stock, refund, free the coupon) must start the same way every time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCanceller {

    private final OrderEventMapper orderEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)   // part of the caller's transaction
    public void cancel(Order order, String reason, ChangedBy cancelledBy) {
        order.cancel(reason, cancelledBy);
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), orderEventMapper.toOrderCancelled(order));
        log.info("Order id={} cancelled by {}: {}", order.getId(), cancelledBy, reason);
    }
}
