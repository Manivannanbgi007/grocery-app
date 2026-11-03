package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A store as GET /api/v1/stores answers it (PublicStoreResponse of store-service).
 *
 * @param openNow worked out by store-service from the opening hours and the current time
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreData(
        Long id,
        String name,
        String description,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String pincode,
        boolean acceptingOrders,
        boolean openNow,
        List<OpeningHoursData> openingHours) {

    /**
     * @param dayOfWeek MONDAY ... SUNDAY
     * @param opensAt   e.g. "07:00:00", null when closed
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpeningHoursData(String dayOfWeek, String opensAt, String closesAt, boolean closed) {
    }
}
