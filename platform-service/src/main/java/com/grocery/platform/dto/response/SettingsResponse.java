package com.grocery.platform.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SettingsResponse(
        String currency,
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryFrom,
        BigDecimal minimumOrderValue,
        String supportEmail,
        String supportPhone,
        Instant updatedAt) {
}
