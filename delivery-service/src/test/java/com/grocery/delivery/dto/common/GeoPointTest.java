package com.grocery.delivery.dto.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoPointTest {

    @Test
    void distanceBetweenTwoPlacesInBengaluru() {
        GeoPoint freshMart = new GeoPoint(12.9784, 77.6408);       // Indiranagar
        GeoPoint greenBasket = new GeoPoint(12.9336, 77.6265);     // Koramangala

        assertThat(freshMart.distanceMetersTo(greenBasket)).isCloseTo(5_200, within(100.0));
        assertThat(freshMart.distanceMetersTo(freshMart)).isZero();
    }

    @Test
    void missingCoordinatesGiveNoPoint() {
        assertThat(GeoPoint.of(new BigDecimal("12.97"), null)).isNull();
        assertThat(GeoPoint.of(new BigDecimal("12.97"), new BigDecimal("77.64"))).isEqualTo(new GeoPoint(12.97, 77.64));
    }
}
