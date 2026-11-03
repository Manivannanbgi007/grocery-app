package com.grocery.store.dto.response;

import com.grocery.store.enums.StoreStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full store details - for the vendor who owns the store, for admins and for other services.
 */
public record StoreResponse(
        Long id,
        Long ownerUserId,
        String name,
        String description,
        String phone,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        StoreStatus status,
        String statusReason,
        BigDecimal commissionPercentage,
        boolean acceptingOrders,
        boolean openNow,
        List<OpeningHoursResponse> openingHours,
        List<String> deliveryPincodes,
        Instant approvedAt,
        Instant createdAt) {
}
