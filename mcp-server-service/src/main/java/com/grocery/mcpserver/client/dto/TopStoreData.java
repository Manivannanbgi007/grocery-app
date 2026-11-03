package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A store with high sales in a period (TopStoreResponse of report-service), for admins.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TopStoreData(String storeName, int orders, BigDecimal sales) {
}
