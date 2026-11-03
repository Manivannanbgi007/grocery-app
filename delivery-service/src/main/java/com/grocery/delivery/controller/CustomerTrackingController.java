package com.grocery.delivery.controller;

import com.grocery.delivery.context.UserContextHolder;
import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.response.TrackingResponse;
import com.grocery.delivery.service.DeliveryTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Customer: follow the delivery of MY order on the map (live tracking, docs/delivery-tracking.md).
 * Allowed role (checked by the API gateway): CUSTOMER. Another customer's order answers 404.
 */
@Tag(name = "Delivery tracking", description = "Follow the delivery of your order live (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/delivery-tracking/orders/{orderId}")
@RequiredArgsConstructor
public class CustomerTrackingController {

    private final DeliveryTrackingService trackingService;

    @Operation(summary = "The delivery of my order now: status, partner, store, my address, the partner's position")
    @GetMapping
    public ResponseEntity<ApiResponse<TrackingResponse>> getTracking(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Tracking fetched successfully",
                trackingService.getCustomerTracking(UserContextHolder.getRequiredUserId(), orderId)));
    }

    /**
     * Server-Sent Events: the same object as GET, at once and again after every change (event name "tracking"), plus a
     * keep-alive comment every 15 s. Closed when the delivery is over, or after 30 minutes (the page reconnects).
     */
    @Operation(summary = "Live updates of my order's delivery (text/event-stream)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable Long orderId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")   // nginx (e.g. a Kubernetes Ingress) must pass each event on at once
                .body(trackingService.openCustomerStream(UserContextHolder.getRequiredUserId(), orderId));
    }
}
