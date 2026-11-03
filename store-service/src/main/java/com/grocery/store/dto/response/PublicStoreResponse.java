package com.grocery.store.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * What customers see about a store. Internal details (owner, commission, status reason) are left out.
 */
public record PublicStoreResponse(
        Long id,
        String name,
        String description,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean acceptingOrders,
        boolean openNow,
        List<OpeningHoursResponse> openingHours) {
}
