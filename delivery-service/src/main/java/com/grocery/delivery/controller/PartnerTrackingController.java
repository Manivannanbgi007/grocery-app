package com.grocery.delivery.controller;

import com.grocery.delivery.context.UserContextHolder;
import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.request.LocationUpdateRequest;
import com.grocery.delivery.dto.response.LocationUpdateResponse;
import com.grocery.delivery.dto.response.TrackingResponse;
import com.grocery.delivery.service.DeliveryTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery partner panel: share MY location while I deliver (live tracking, docs/delivery-tracking.md).
 * Allowed role (checked by the API gateway): DELIVERY_PARTNER.
 */
@Tag(name = "Delivery partner - Live tracking", description = "Share your location during a delivery (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery/deliveries")
@RequiredArgsConstructor
public class PartnerTrackingController {

    private final DeliveryTrackingService trackingService;

    @Operation(summary = "Where I am now (send every few seconds while the delivery is accepted, picked up or on its way)")
    @PutMapping("/{deliveryId}/location")
    public ResponseEntity<ApiResponse<LocationUpdateResponse>> updateLocation(@PathVariable Long deliveryId,
                                                                             @Valid @RequestBody LocationUpdateRequest request) {
        LocationUpdateResponse response = trackingService.updateLocation(UserContextHolder.getRequiredUserId(), deliveryId, request);
        return ResponseEntity.ok(ApiResponse.success(response.stored() ? "Location updated" : "Location skipped", response));
    }

    @Operation(summary = "My delivery on the map: store, customer, my last shared position and the route")
    @GetMapping("/{deliveryId}/tracking")
    public ResponseEntity<ApiResponse<TrackingResponse>> getTracking(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Tracking fetched successfully",
                trackingService.getPartnerTracking(UserContextHolder.getRequiredUserId(), deliveryId)));
    }
}
