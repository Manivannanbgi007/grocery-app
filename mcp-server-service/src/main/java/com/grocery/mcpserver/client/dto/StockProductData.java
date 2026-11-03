package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One of the vendor's own products with its stock (GET /api/v1/vendor/products, ProductResponse of
 * product-read-service), only the fields the low-stock tool uses.
 *
 * @param status            ACTIVE, INACTIVE (hidden by the vendor) or BLOCKED (hidden by an admin)
 * @param availableQuantity units customers can still buy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockProductData(String name, String unit, BigDecimal unitQuantity, String status, int availableQuantity,
                               boolean inStock) {
}
