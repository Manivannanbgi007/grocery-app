package com.grocery.delivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a partner earned between two dates (both included), with one line per day.
 */
public record EarningsResponse(LocalDate from, LocalDate to, int deliveries, BigDecimal total, List<DailyEarning> days) {

    public record DailyEarning(LocalDate date, int deliveries, BigDecimal amount) {
    }
}
