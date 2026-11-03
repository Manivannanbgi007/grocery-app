package com.grocery.delivery.dto.common;

import java.math.BigDecimal;

/**
 * A point on the map: latitude and longitude in degrees (WGS84, the system GPS and OpenStreetMap use).
 * In JSON: { "latitude": 12.9784, "longitude": 77.6408 }.
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    /** Null when either value is missing (not every store or address has coordinates). */
    public static GeoPoint of(BigDecimal latitude, BigDecimal longitude) {
        return latitude == null || longitude == null ? null : new GeoPoint(latitude.doubleValue(), longitude.doubleValue());
    }

    /** Straight-line distance in meters (haversine formula): how far apart two points are, ignoring roads. */
    public double distanceMetersTo(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
