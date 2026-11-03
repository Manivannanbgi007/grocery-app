package com.grocery.delivery.dto.response;

import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.VehicleType;

import java.time.Instant;

/**
 * Everything the tracking map shows, in one object. The live stream sends this same object again after every change
 * (a new location or a new status), so a page simply replaces what it shows.
 *
 * @param live        true while the partner shares their location (from accepting until handing over)
 * @param partner     null until a partner is assigned
 * @param location    the partner's last position; null when not live or nothing was shared yet
 * @param route       null without a routing service (see TrackingRoute)
 * @param generatedAt when this snapshot was made
 */
public record TrackingResponse(
        Long orderId,
        Long deliveryId,
        DeliveryStatus status,
        boolean live,
        PartnerInfo partner,
        Place pickup,
        Place destination,
        LiveLocation location,
        TrackingRoute route,
        Instant generatedAt) {

    /** Who brings the order (the customer can call them). */
    public record PartnerInfo(String name, String phone, VehicleType vehicleType) {
    }

    /** @param location null when the store or the address has no coordinates */
    public record Place(String name, String address, GeoPoint location) {
    }
}
