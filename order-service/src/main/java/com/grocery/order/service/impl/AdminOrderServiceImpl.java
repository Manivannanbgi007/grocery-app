package com.grocery.order.service.impl;

import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.exception.ResourceNotFoundException;
import com.grocery.order.mapper.OrderMapper;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.AdminOrderService;
import com.grocery.order.service.InvoiceService;
import com.grocery.order.service.OrderCanceller;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderCanceller orderCanceller;
    private final InvoiceService invoiceService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> searchOrders(Long customerUserId, Long storeId, OrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(customerUserId, storeId, status, pageable).map(orderMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return orderMapper.toResponse(findOrder(orderId));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Order order = findOrder(orderId);
        orderCanceller.cancel(order, reason.trim(), ChangedBy.ADMIN);
        return orderMapper.toResponse(order);
    }

    /** InvoiceService runs its own short transactions. */
    @Override
    public InvoiceFile getInvoice(Long orderId) {
        return invoiceService.getInvoice(() -> findOrder(orderId));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
