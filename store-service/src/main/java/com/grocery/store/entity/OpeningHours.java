package com.grocery.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Opening hours of a store for one day of the week (a row in store_opening_hours).
 * It has no id of its own: it is part of the Store (an @Embeddable value object).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class OpeningHours {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    // A time of day ("opens at 07:00"), not a moment: it must be stored exactly as it is. Without @JdbcType, Hibernate
    // converts TIME values from the JVM's time zone to hibernate.jdbc.time_zone (UTC), so on a PC set to India time
    // 07:00 was stored as 01:30 (and 07:00 from the seed data was read as 12:30). LocalTimeJdbcType hands the LocalTime
    // to the JDBC driver unchanged.
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "opens_at")
    private LocalTime opensAt;

    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "closes_at")
    private LocalTime closesAt;

    /** true = the store is closed all day. */
    @Column(nullable = false)
    private boolean closed;

    public static OpeningHours open(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        return new OpeningHours(dayOfWeek, opensAt, closesAt, false);
    }

    public static OpeningHours closedAllDay(DayOfWeek dayOfWeek) {
        return new OpeningHours(dayOfWeek, null, null, true);
    }

    /** Open from opensAt (inclusive) until closesAt (exclusive). */
    public boolean isOpenAt(LocalTime time) {
        return !closed && !time.isBefore(opensAt) && time.isBefore(closesAt);
    }
}
