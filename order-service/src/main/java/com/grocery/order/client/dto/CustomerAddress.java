package com.grocery.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One address of the customer, from customer-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerAddress(
        Long id,
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude) {
}
