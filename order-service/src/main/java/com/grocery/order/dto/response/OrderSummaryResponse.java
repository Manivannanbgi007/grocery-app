package com.grocery.order.dto.response;

import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of an order list (order history, the store's order queue, the admin's order list).
 *
 * @param itemCount number of units in the order
 */
public record OrderSummaryResponse(
        Long id,
        Long customerUserId,
        Long storeId,
        String storeName,
        OrderStatus status,
        PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        int itemCount,
        BigDecimal totalAmount,
        Instant placedAt) {
}
