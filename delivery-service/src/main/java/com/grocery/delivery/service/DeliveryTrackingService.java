package com.grocery.delivery.service;

import com.grocery.delivery.dto.request.LocationUpdateRequest;
import com.grocery.delivery.dto.response.LocationUpdateResponse;
import com.grocery.delivery.dto.response.TrackingResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live delivery tracking: the partner shares their location, the customer follows it (docs/delivery-tracking.md). */
public interface DeliveryTrackingService {

    /** The partner's phone reports where it is (only for the partner's own delivery, only while it is tracked). */
    LocationUpdateResponse updateLocation(Long partnerUserId, Long deliveryId, LocationUpdateRequest request);

    /** The partner's map: their delivery with the last stored position and route. */
    TrackingResponse getPartnerTracking(Long partnerUserId, Long deliveryId);

    /** The customer's map, once: only for the customer's own order. */
    TrackingResponse getCustomerTracking(Long customerUserId, Long orderId);

    /** The customer's live stream: the snapshot now, then one after every change (only for the customer's own order). */
    SseEmitter openCustomerStream(Long customerUserId, Long orderId);
}
