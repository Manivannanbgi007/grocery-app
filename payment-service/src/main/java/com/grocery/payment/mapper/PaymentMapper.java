package com.grocery.payment.mapper;

import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerUserId(),
                payment.getStoreId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getStatus() == PaymentStatus.AWAITING_PAYMENT,
                payment.getGatewayOrderId(),
                payment.getGatewayPaymentId(),
                payment.getPayBefore(),
                payment.getFailureReason(),
                payment.getRefundReference(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                payment.getCreatedAt());
    }
}
