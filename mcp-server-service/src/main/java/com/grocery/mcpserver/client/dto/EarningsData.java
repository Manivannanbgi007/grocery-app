package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a delivery partner earned from `from` to `to` (GET /api/v1/delivery/earnings, EarningsResponse of
 * delivery-service). Only days with deliveries are listed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EarningsData(LocalDate from, LocalDate to, int deliveries, BigDecimal total, List<Day> days) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(LocalDate date, int deliveries, BigDecimal amount) {
    }
}
