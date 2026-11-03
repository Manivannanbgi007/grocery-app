package com.grocery.payment.controller;

import com.grocery.payment.context.UserContextHolder;
import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.dto.request.RazorpayVerifyRequest;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.RazorpayCheckoutResponse;
import com.grocery.payment.service.RazorpayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel, Razorpay mode: open Razorpay Checkout and confirm the payment afterwards.
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Payments", description = "The payment of your order (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
public class RazorpayPaymentController {

    private final RazorpayPaymentService razorpayPaymentService;

    @Operation(summary = "RAZORPAY: what the frontend passes to Razorpay Checkout (key id, order id, amount in paise)")
    @GetMapping("/orders/{orderId}/razorpay-checkout")
    public ResponseEntity<ApiResponse<RazorpayCheckoutResponse>> getCheckout(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Checkout details ready",
                razorpayPaymentService.getCheckout(UserContextHolder.getRequiredUserId(), orderId)));
    }

    @Operation(summary = "RAZORPAY: confirm the payment with what Checkout returned (the signature is checked)")
    @PostMapping("/orders/{orderId}/razorpay-verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verify(@PathVariable Long orderId,
                                                               @Valid @RequestBody RazorpayVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment successful",
                razorpayPaymentService.verifyCheckout(UserContextHolder.getRequiredUserId(), orderId, request)));
    }
}
