package com.grocery.delivery.dto.response;

import java.math.BigDecimal;

import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.enums.VehicleType;

/**
 * @param online right now (Redis)
 */
public record PartnerResponse(
        Long userId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String vehicleNumber,
        String drivingLicenseNumber,
        Long zoneId,
        String zoneName,
        KycStatus kycStatus,
        String kycNote,
        boolean online,
        BigDecimal ratingAverage,
        int ratingCount) {
}
