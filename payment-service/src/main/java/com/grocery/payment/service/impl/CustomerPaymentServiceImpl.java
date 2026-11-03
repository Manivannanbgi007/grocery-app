package com.grocery.payment.service.impl;

import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.mapper.PaymentMapper;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.CustomerPaymentService;
import com.grocery.payment.service.OnlinePaymentRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPaymentServiceImpl implements CustomerPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OnlinePaymentRecorder onlinePaymentRecorder;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getMyPayment(Long customerUserId, Long orderId) {
        return paymentMapper.toResponse(findMyPayment(customerUserId, orderId));
    }

    @Override
    @Transactional
    public PaymentResponse payWithMockGateway(Long customerUserId, Long orderId, boolean success) {
        Payment payment = findMyPayment(customerUserId, orderId);
        payment.checkCanBePaidNow();

        if (success) {
            onlinePaymentRecorder.paid(payment, "mock_pay_" + UUID.randomUUID());
        } else {
            onlinePaymentRecorder.failed(payment, "The bank declined the payment (mock)");
        }
        return paymentMapper.toResponse(payment);
    }

    /** Another customer's payment looks like it does not exist. */
    private Payment findMyPayment(Long customerUserId, Long orderId) {
        return paymentRepository.findByOrderIdAndCustomerUserId(orderId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + orderId
                        + ". If you have just placed it, try again in a few seconds."));
    }
}
