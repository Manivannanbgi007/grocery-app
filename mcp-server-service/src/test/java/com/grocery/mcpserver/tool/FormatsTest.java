package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.dto.StoreData.OpeningHoursData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    @Test
    void rupeesWithoutPaiseForWholeAmounts() {
        assertThat(Formats.rupees(new BigDecimal("36.00"))).isEqualTo("₹36");
        assertThat(Formats.rupees(new BigDecimal("28.50"))).isEqualTo("₹28.50");
        assertThat(Formats.rupees(new BigDecimal("195.5"))).isEqualTo("₹195.50");
        assertThat(Formats.rupees(new BigDecimal("1250"))).isEqualTo("₹1,250");
        assertThat(Formats.rupees(null)).isNull();
    }

    @Test
    void percentAndPackSize() {
        assertThat(Formats.percent(new BigDecimal("10.00"))).isEqualTo("10%");
        assertThat(Formats.percent(new BigDecimal("12.50"))).isEqualTo("12.5%");
        assertThat(Formats.packSize("G", new BigDecimal("500.000"))).isEqualTo("500 g");
        assertThat(Formats.packSize("KG", new BigDecimal("1.000"))).isEqualTo("1 kg");
        assertThat(Formats.packSize("PIECE", new BigDecimal("6"))).isEqualTo("6 pcs");
        assertThat(Formats.packSize("PIECE", BigDecimal.ONE)).isEqualTo("1 pc");
        assertThat(Formats.packSize("DOZEN", BigDecimal.ONE)).isEqualTo("1 dozen");
    }

    @Test
    void datesAreShownInIndiaTime() {
        // 23:59 UTC on 31 Dec is already 1 Jan in India (UTC+5:30)
        assertThat(Formats.date(Instant.parse("2027-12-31T23:59:59Z"))).isEqualTo("1 Jan 2028");
        assertThat(Formats.date(Instant.parse("2026-10-26T10:00:00Z"))).isEqualTo("26 Oct 2026");
    }

    @Test
    void openingHoursGroupDaysWithTheSameHours() {
        List<OpeningHoursData> week = new ArrayList<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")) {
            week.add(new OpeningHoursData(day, "08:00:00", "21:00:00", false));
        }
        week.add(new OpeningHoursData("SUNDAY", "09:00:00", "14:00:00", false));

        assertThat(Formats.openingHours(week)).containsExactly("Monday-Saturday: 08:00-21:00", "Sunday: 09:00-14:00");
    }

    @Test
    void openingHoursSayEveryDayOrClosed() {
        List<OpeningHoursData> everyDay = new ArrayList<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            everyDay.add(new OpeningHoursData(day, "07:00:00", "22:00:00", false));
        }
        assertThat(Formats.openingHours(everyDay)).containsExactly("Every day: 07:00-22:00");

        everyDay.set(6, new OpeningHoursData("SUNDAY", null, null, true));
        assertThat(Formats.openingHours(everyDay)).containsExactly("Monday-Saturday: 07:00-22:00", "Sunday: closed");
    }
}
