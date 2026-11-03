package com.grocery.cart.controller;

import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.dto.response.CheckoutSummaryResponse;
import com.grocery.cart.service.CheckoutService;
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
@RequestMapping("/internal/carts")
@RequiredArgsConstructor
public class InternalCartController {

    private final CheckoutService checkoutService;

    @Operation(summary = "The customer's cart checked against the current catalog (used by order-service to place an order)")
    @GetMapping("/{customerUserId}/checkout")
    public ResponseEntity<ApiResponse<CheckoutSummaryResponse>> getCheckoutSummary(@PathVariable Long customerUserId) {
        return ResponseEntity.ok(ApiResponse.success("Checkout summary prepared", checkoutService.prepareCheckout(customerUserId)));
    }
}
