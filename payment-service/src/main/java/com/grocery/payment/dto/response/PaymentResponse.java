package com.grocery.payment.dto.response;

import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param canPayNow      true when the customer should pay now (ONLINE, stock reserved)
 * @param gatewayOrderId what the customer pays against at the gateway
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        Long customerUserId,
        Long storeId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        boolean canPayNow,
        String gatewayOrderId,
        String gatewayPaymentId,
        Instant payBefore,
        String failureReason,
        String refundReference,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt) {
}
