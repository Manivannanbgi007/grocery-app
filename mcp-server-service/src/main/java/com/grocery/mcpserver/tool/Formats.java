package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.dto.StoreData.OpeningHoursData;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns numbers and codes into the short texts the tools return: "₹56", "500 g", "31 Dec 2027".
 *
 * The tools return prices already formatted, so the model copies them instead of calculating or rounding them
 * (and mcp-client-service can check that every ₹ amount in an answer came from a tool).
 */
public final class Formats {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(INDIA);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(INDIA);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
    /** An amount written into a text by a service: "138.00" (not "₹138.00", not part of "1,138.005") */
    private static final Pattern BARE_AMOUNT = Pattern.compile("(?<![₹\\d.,])\\b(\\d+\\.\\d{2})(?!\\.?\\d)");

    private Formats() {
    }

    /** A day of a report (already an India date): "27 Oct 2026". */
    public static String day(LocalDate date) {
        return date == null ? null : DAY.format(date);
    }

    /** 36 -> "₹36", 28.5 -> "₹28.50", 1250 -> "₹1,250". Whole amounts without paise, like the website. */
    public static String rupees(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        // A new DecimalFormat each time: DecimalFormat is not thread-safe
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", SYMBOLS);
        return "₹" + format.format(amount);
    }

    /**
     * "Please pay 138.00 in cash" -> "Please pay ₹138 in cash": the amounts other services write into their texts (two
     * decimals, no currency; no other number in those texts has decimals) as ₹ amounts, like every tool returns them.
     * The assistant then quotes them as ₹ amounts, and its answer check (mcp-client-service) finds them in the result.
     */
    public static String withRupees(String text) {
        if (text == null) {
            return null;
        }
        return BARE_AMOUNT.matcher(text).replaceAll(match -> Matcher.quoteReplacement(rupees(new BigDecimal(match.group(1)))));
    }

    /** 10.00 -> "10%" */
    public static String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    /** ("G", 500) -> "500 g", ("PIECE", 6) -> "6 pcs", ("DOZEN", 1) -> "1 dozen" (the same texts as the website). */
    public static String packSize(String unit, BigDecimal quantity) {
        if (unit == null || quantity == null) {
            return null;
        }
        String amount = quantity.stripTrailingZeros().toPlainString();
        boolean one = quantity.compareTo(BigDecimal.ONE) == 0;
        return switch (unit) {
            case "KG" -> amount + " kg";
            case "G" -> amount + " g";
            case "L" -> amount + " L";
            case "ML" -> amount + " ml";
            case "PIECE" -> one ? "1 pc" : amount + " pcs";
            case "PACK" -> one ? "1 pack" : amount + " packs";
            case "DOZEN" -> amount + " dozen";
            default -> amount + " " + unit.toLowerCase(Locale.ROOT);
        };
    }

    /** An instant as an India date: "31 Dec 2027". */
    public static String date(Instant instant) {
        return instant == null ? null : DATE.format(instant);
    }

    /** An instant as an India date and time: "26 Oct 2026, 8:27 pm". */
    public static String dateTime(Instant instant) {
        return instant == null ? null : DATE_TIME.format(instant).replace("AM", "am").replace("PM", "pm");
    }

    /**
     * Opening hours in a few short lines, days with the same hours grouped:
     * ["Monday-Saturday: 08:00-21:00", "Sunday: 09:00-14:00"], or ["Every day: 07:00-22:00"].
     */
    public static List<String> openingHours(List<OpeningHoursData> days) {
        List<String> lines = new ArrayList<>();
        if (days == null || days.isEmpty()) {
            return lines;
        }
        int groupStart = 0;
        for (int i = 1; i <= days.size(); i++) {
            boolean groupEnds = i == days.size() || !Objects.equals(hoursOf(days.get(i)), hoursOf(days.get(groupStart)));
            if (groupEnds) {
                String from = dayName(days.get(groupStart).dayOfWeek());
                String to = dayName(days.get(i - 1).dayOfWeek());
                String dayRange = groupStart == i - 1 ? from : from + "-" + to;
                lines.add(dayRange + ": " + hoursOf(days.get(groupStart)));
                groupStart = i;
            }
        }
        if (lines.size() == 1 && days.size() == 7) {
            return List.of("Every day: " + hoursOf(days.get(0)));
        }
        return lines;
    }

    /** "08:00-21:00", or "closed" */
    private static String hoursOf(OpeningHoursData day) {
        if (day.closed() || day.opensAt() == null || day.closesAt() == null) {
            return "closed";
        }
        return shortTime(day.opensAt()) + "-" + shortTime(day.closesAt());
    }

    /** "07:00:00" -> "07:00" */
    private static String shortTime(String time) {
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    /** "MONDAY" -> "Monday" */
    private static String dayName(String dayOfWeek) {
        String lower = dayOfWeek.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
