package com.grocery.delivery.controller;

import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.service.AdminDeliveryService;
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
@RequestMapping("/internal/deliveries")
@RequiredArgsConstructor
public class InternalDeliveryController {

    private final AdminDeliveryService adminDeliveryService;

    @Operation(summary = "The delivery of an order")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getDeliveryOfOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Delivery fetched successfully", adminDeliveryService.getDeliveryOfOrder(orderId)));
    }
}
