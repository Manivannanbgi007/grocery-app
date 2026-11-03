package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A best-selling product of a period (TopProductResponse of report-service).
 *
 * @param quantity units sold
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TopProductData(String productName, int quantity, BigDecimal sales) {
}
