package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.EarningsData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;

/**
 * What getPartnerEarnings returns: the deliveries and the money of a period, and the days with deliveries.
 *
 * @param period e.g. "the last 7 days, 21 Oct 2026 to 27 Oct 2026"
 * @param perDay only the days with deliveries, oldest first
 */
public record PartnerEarnings(String period, int deliveries, String earned, List<Day> perDay) {

    public record Day(String date, int deliveries, String earned) {
    }

    public static PartnerEarnings from(String period, EarningsData earnings) {
        return new PartnerEarnings(period, earnings.deliveries(), Formats.rupees(earnings.total()),
                earnings.days().stream()
                        .map(day -> new Day(Formats.day(day.date()), day.deliveries(), Formats.rupees(day.amount())))
                        .toList());
    }
}
