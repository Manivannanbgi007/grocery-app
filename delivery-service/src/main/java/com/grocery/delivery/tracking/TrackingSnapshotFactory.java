package com.grocery.delivery.tracking;

import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.dto.response.TrackingResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Builds the TrackingResponse ("snapshot") of a delivery: the delivery from MySQL, the partner's last position and
 * route from Redis. Used for the first answer of a tracking page and for every live update.
 */
@Component
@RequiredArgsConstructor
public class TrackingSnapshotFactory {

    private final DeliveryPartnerRepository partnerRepository;
    private final LiveLocationStore liveLocationStore;
    private final Clock clock;

    /** With the position stored in Redis (none outside the tracked statuses). */
    public TrackingResponse build(Delivery delivery) {
        LiveTracking tracking = delivery.getStatus().isTracked()
                ? liveLocationStore.find(delivery.getId()).orElse(null)
                : null;
        return build(delivery, tracking);
    }

    /** With a position the caller already has (a location update that was just stored). */
    public TrackingResponse build(Delivery delivery, LiveTracking tracking) {
        TrackingResponse.PartnerInfo partner = delivery.getPartnerUserId() == null ? null
                : partnerRepository.findByUserId(delivery.getPartnerUserId())
                        .map(p -> new TrackingResponse.PartnerInfo(p.getFullName(), p.getPhone(), p.getVehicleType()))
                        .orElse(null);
        TrackingResponse.Place pickup = new TrackingResponse.Place(delivery.getStoreName(), delivery.getStoreAddress(),
                GeoPoint.of(delivery.getPickupLatitude(), delivery.getPickupLongitude()));
        TrackingResponse.Place destination = new TrackingResponse.Place(delivery.getCustomerName(), delivery.getCustomerAddress(),
                GeoPoint.of(delivery.getDropLatitude(), delivery.getDropLongitude()));
        return new TrackingResponse(
                delivery.getOrderId(),
                delivery.getId(),
                delivery.getStatus(),
                delivery.getStatus().isTracked(),
                partner,
                pickup,
                destination,
                tracking == null ? null : tracking.location(),
                tracking == null ? null : tracking.route(),
                Instant.now(clock));
    }
}
