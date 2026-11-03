package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * The platform settings as GET /api/v1/platform/settings answers them (platform-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingsData(
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryFrom,
        BigDecimal minimumOrderValue,
        String supportEmail,
        String supportPhone) {
}
