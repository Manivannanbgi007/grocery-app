package com.grocery.order.service;

import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.request.CancelOrderRequest;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * The customer's own orders. Every method takes the customer's user id (X-User-Id).
 */
public interface CustomerOrderService {

    /**
     * Turns the cart into a PENDING order and publishes OrderCreated (the saga starts).
     *
     * @param idempotencyKey optional (the Idempotency-Key header): a repeat of the request with the same key gets the
     *                       order the first one placed, never a second order
     */
    OrderResponse placeOrder(Long customerUserId, PlaceOrderRequest request, String idempotencyKey);

    PageResponse<OrderSummaryResponse> listMyOrders(Long customerUserId, OrderStatus status, Pageable pageable);

    OrderResponse getMyOrder(Long customerUserId, Long orderId);

    OrderResponse cancelMyOrder(Long customerUserId, Long orderId, CancelOrderRequest request);

    InvoiceFile getMyInvoice(Long customerUserId, Long orderId);
}
