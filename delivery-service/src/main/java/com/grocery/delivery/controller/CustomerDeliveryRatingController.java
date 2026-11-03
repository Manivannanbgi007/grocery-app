package com.grocery.delivery.controller;

import com.grocery.delivery.context.UserContextHolder;
import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.request.DeliveryRatingRequest;
import com.grocery.delivery.dto.response.DeliveryRatingResponse;
import com.grocery.delivery.service.DeliveryRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel: rate the delivery of my order.
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Delivery ratings", description = "Rate the delivery of your order (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/delivery-ratings")
@RequiredArgsConstructor
public class CustomerDeliveryRatingController {

    private final DeliveryRatingService deliveryRatingService;

    @Operation(summary = "Rate the delivery of a delivered order (1-5 stars, once)")
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryRatingResponse>> rateDelivery(@Valid @RequestBody DeliveryRatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Thank you for rating the delivery",
                deliveryRatingService.rateDelivery(UserContextHolder.getRequiredUserId(), request)));
    }

    @Operation(summary = "My rating of an order's delivery (rating is null until I rate it)")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryRatingResponse>> getMyRating(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Rating fetched successfully",
                deliveryRatingService.getMyRating(UserContextHolder.getRequiredUserId(), orderId)));
    }
}
