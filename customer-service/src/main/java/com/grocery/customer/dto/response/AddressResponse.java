package com.grocery.customer.dto.response;

import com.grocery.customer.enums.AddressLabel;

import java.math.BigDecimal;

public record AddressResponse(
        Long id,
        AddressLabel label,
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean defaultAddress) {
}
