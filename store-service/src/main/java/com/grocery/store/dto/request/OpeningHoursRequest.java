package com.grocery.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Opening hours for one day. When closed is true, opensAt and closesAt are not needed.
 * "closed" is optional (a Boolean, not a boolean): leaving it out means the store is open that day.
 */
public record OpeningHoursRequest(

        @Schema(example = "MONDAY")
        @NotNull
        DayOfWeek dayOfWeek,

        @Schema(type = "string", example = "07:00")
        LocalTime opensAt,

        @Schema(type = "string", example = "22:00")
        LocalTime closesAt,

        @Schema(description = "true = closed all day (optional, default false)", example = "false")
        Boolean closed) {

    /** Optional flag: a missing value means false (open). */
    public boolean closedAllDay() {
        return Boolean.TRUE.equals(closed);
    }
}
