package com.grocery.delivery.tracking;

import com.grocery.delivery.dto.response.LiveLocation;
import com.grocery.delivery.dto.response.TrackingRoute;

/**
 * What Redis keeps per delivery while it is tracked: the partner's last position and the route from there
 * (route is null without a routing service).
 */
public record LiveTracking(LiveLocation location, TrackingRoute route) {
}
