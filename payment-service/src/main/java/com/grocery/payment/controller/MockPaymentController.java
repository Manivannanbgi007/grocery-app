package com.grocery.payment.controller;

import com.grocery.payment.context.UserContextHolder;
import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.dto.request.MockPaymentRequest;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.service.CustomerPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for the payment page of a real gateway. Only exists while app.payment.gateway = mock.
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Payments", description = "The payment of your order (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentController {

    private final CustomerPaymentService customerPaymentService;

    @Operation(summary = "MOCK gateway: pay my order online (success=true) or let the bank decline it (success=false)")
    @PostMapping("/orders/{orderId}/mock-pay")
    public ResponseEntity<ApiResponse<PaymentResponse>> mockPay(@PathVariable Long orderId, @Valid @RequestBody MockPaymentRequest request) {
        PaymentResponse payment = customerPaymentService.payWithMockGateway(UserContextHolder.getRequiredUserId(), orderId, request.success());
        return ResponseEntity.ok(ApiResponse.success(request.success() ? "Payment successful" : "Payment failed", payment));
    }
}
