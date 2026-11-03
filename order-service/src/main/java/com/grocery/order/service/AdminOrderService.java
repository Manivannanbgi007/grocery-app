package com.grocery.order.service;

import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * Admins see every order and resolve problems. The delivery steps (OUT_FOR_DELIVERY, DELIVERED)
 * come from delivery-service's events, not from admins.
 */
public interface AdminOrderService {

    /** Every filter is optional. */
    PageResponse<OrderSummaryResponse> searchOrders(Long customerUserId, Long storeId, OrderStatus status, Pageable pageable);

    OrderResponse getOrder(Long orderId);

    OrderResponse cancelOrder(Long orderId, String reason);

    InvoiceFile getInvoice(Long orderId);
}
