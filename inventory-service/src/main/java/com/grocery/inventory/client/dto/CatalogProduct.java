package com.grocery.inventory.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The few product fields inventory-service needs from product-read-service.
 * The response has more fields; ignoreUnknown skips them.
 *
 * @param status ACTIVE, INACTIVE, BLOCKED or DELETED
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProduct(Long id, Long storeId, String name, String status) {

    public boolean isDeleted() {
        return "DELETED".equals(status);
    }
}
