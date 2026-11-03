package com.grocery.mcpserver.tool;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The last few days up to today (India dates, both ends included), for the sales and earnings tools:
 * days = 1 is today, days = 7 the last 7 days.
 */
record DateRange(LocalDate from, LocalDate to) {

    static final int DEFAULT_DAYS = 7;
    static final int MAX_DAYS = 90;
    static final String DAYS_RULE = "days must be a whole number from 1 to " + MAX_DAYS
            + " (1 = today, 7 = the last 7 days, 30 = the last 30 days).";

    /** null means the default of 7 days */
    static boolean isValid(Integer days) {
        return days == null || (days >= 1 && days <= MAX_DAYS);
    }

    static DateRange lastDays(Integer days, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        int count = days == null ? DEFAULT_DAYS : days;
        return new DateRange(today.minusDays(count - 1L), today);
    }

    /** "today, 27 Oct 2026" or "the last 7 days, 21 Oct 2026 to 27 Oct 2026": the period, for the model to repeat. */
    String text() {
        if (from.equals(to)) {
            return "today, " + Formats.day(to);
        }
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        return "the last " + days + " days, " + Formats.day(from) + " to " + Formats.day(to);
    }
}
