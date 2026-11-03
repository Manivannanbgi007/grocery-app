package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A delivery partner and their KYC (PartnerResponse of delivery-service): the partner's own profile
 * (GET /api/v1/delivery/profile) or one partner in the admins' list (GET /api/v1/admin/delivery-partners).
 * Only the fields the tools use: no phone, licence or vehicle number.
 *
 * @param vehicleType   BICYCLE, SCOOTER or MOTORBIKE; null before the KYC is sent
 * @param kycStatus     NOT_SUBMITTED, PENDING, APPROVED or REJECTED
 * @param kycNote       the reason of a rejected KYC, otherwise null
 * @param ratingAverage null before the first rating
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerData(String fullName, String vehicleType, String zoneName, String kycStatus, String kycNote,
                          boolean online, BigDecimal ratingAverage, int ratingCount) {
}
