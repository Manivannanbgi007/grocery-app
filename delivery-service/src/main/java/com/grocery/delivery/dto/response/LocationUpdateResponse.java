package com.grocery.delivery.dto.response;

/**
 * The answer to a location update from the partner's phone.
 *
 * @param stored false when the update was skipped: it came too soon after the previous one, or a newer position is
 *               already stored (a phone that was offline may send old positions late). Not an error: keep sending.
 * @param route  the current route, for the partner's map (null without a routing service)
 */
public record LocationUpdateResponse(boolean stored, TrackingRoute route) {
}
