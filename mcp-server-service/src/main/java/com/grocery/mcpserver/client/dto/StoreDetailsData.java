package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * A store as its vendor (GET /api/v1/vendor/store) and the admins (GET /api/v1/admin/stores) see it (StoreResponse
 * of store-service), only the fields the tools use.
 *
 * @param status       PENDING_APPROVAL, ACTIVE, REJECTED or DISABLED
 * @param statusReason why an admin rejected or disabled the store, otherwise null
 * @param openNow      worked out by store-service from the opening hours and the current time
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreDetailsData(Long id, String name, String city, String status, String statusReason,
                               boolean acceptingOrders, boolean openNow, Instant createdAt) {
}
