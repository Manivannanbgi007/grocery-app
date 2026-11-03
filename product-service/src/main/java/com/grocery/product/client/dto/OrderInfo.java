package com.grocery.product.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The fields of order-service's order that a review needs: whose order, delivered or not, which products.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderInfo(Long id, Long customerUserId, Long storeId, String status, List<Item> items, Address deliveryAddress) {

    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }

    public boolean contains(Long productId) {
        return items != null && items.stream().anyMatch(item -> productId.equals(item.productId()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productId, String productName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String contactName) {
    }
}
