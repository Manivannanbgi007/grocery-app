package com.grocery.payment.controller;

import com.grocery.payment.context.UserContextHolder;
import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.service.CustomerPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel: the payment of MY order (the customer comes from X-User-Id).
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Payments", description = "The payment of your order (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final CustomerPaymentService customerPaymentService;

    @Operation(summary = "The payment of my order (canPayNow = true when an online payment is waiting for me)")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getMyPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully",
                customerPaymentService.getMyPayment(UserContextHolder.getRequiredUserId(), orderId)));
    }
}
