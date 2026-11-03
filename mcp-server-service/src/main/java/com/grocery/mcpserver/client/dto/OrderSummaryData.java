package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order in the customer's order list from order-service (GET /api/v1/orders), only the fields the tools need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSummaryData(long id, String storeName, String status, int itemCount, BigDecimal totalAmount,
                               Instant placedAt) {
}
