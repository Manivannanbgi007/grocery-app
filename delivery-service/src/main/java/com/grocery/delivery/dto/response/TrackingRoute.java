package com.grocery.delivery.dto.response;

import com.grocery.delivery.dto.common.GeoPoint;

import java.time.Instant;
import java.util.List;

/**
 * The way along the streets from the partner to where they are going next, from the routing service (OSRM).
 * Only present when a routing service is configured (app.tracking.routing.url); without one there is no route and
 * no arrival time - the map then draws a straight dashed line.
 *
 * @param target             PICKUP (on the way to the store) or DESTINATION (on the way to the customer)
 * @param durationSeconds    the routing service's driving time
 * @param estimatedArrivalAt computedAt + duration: the "arrives around 10:42" on the tracking page
 * @param points             the line to draw: [[latitude, longitude], ...]
 * @param from               where the partner was when it was computed (it is computed again after they moved on)
 */
public record TrackingRoute(
        Target target,
        double distanceMeters,
        double durationSeconds,
        Instant estimatedArrivalAt,
        List<double[]> points,
        GeoPoint from,
        Instant computedAt) {

    public enum Target { PICKUP, DESTINATION }
}
