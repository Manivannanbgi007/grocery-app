package com.grocery.order.service.impl;

import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.exception.ResourceNotFoundException;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.mapper.OrderMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.InvoiceService;
import com.grocery.order.service.VendorOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store workflow: CONFIRMED -> accept (or reject) -> pack -> ready for pickup.
 * Each step changes the order (the entity checks the transition) and publishes its event in the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorOrderServiceImpl implements VendorOrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InvoiceService invoiceService;
    private final OrderEventMapper orderEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listStoreOrders(Long storeId, OrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(null, storeId, status, pageable).map(orderMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getStoreOrder(Long storeId, Long orderId) {
        return orderMapper.toResponse(findStoreOrder(storeId, orderId));
    }

    @Override
    @Transactional
    public OrderResponse acceptOrder(Long storeId, Long orderId) {
        Order order = findStoreOrder(storeId, orderId);
        order.accept();
        publish(order, orderEventMapper.toOrderAccepted(order));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse rejectOrder(Long storeId, Long orderId, String reason) {
        Order order = findStoreOrder(storeId, orderId);
        order.reject(reason.trim());
        publish(order, orderEventMapper.toOrderRejected(order));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse packOrder(Long storeId, Long orderId) {
        Order order = findStoreOrder(storeId, orderId);
        order.pack();
        publish(order, orderEventMapper.toOrderPacked(order));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markReadyForPickup(Long storeId, Long orderId) {
        Order order = findStoreOrder(storeId, orderId);
        order.markReadyForPickup();
        publish(order, orderEventMapper.toOrderReadyForPickup(order));
        return orderMapper.toResponse(order);
    }

    private void publish(Order order, SpecificRecordBase event) {
        outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, order.getId(), event);
        log.info("Store id={}: order id={} is now {}", order.getStoreId(), order.getId(), order.getStatus());
    }

    /** Another store's order looks like it does not exist. InvoiceService runs its own short transactions. */
    @Override
    public InvoiceFile getInvoice(Long storeId, Long orderId) {
        return invoiceService.getInvoice(() -> findStoreOrder(storeId, orderId));
    }

    private Order findStoreOrder(Long storeId, Long orderId) {
        return orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
