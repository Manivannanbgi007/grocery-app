package com.grocery.payment.controller;

import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. /internal/** is never routed by the API gateway.
 */
@Tag(name = "Internal", description = "For other microservices only - not reachable through the API gateway")
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentQueryService paymentQueryService;

    @Operation(summary = "The payment of an order")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentOfOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully", paymentQueryService.getPaymentOfOrder(orderId)));
    }
}
