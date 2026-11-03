package com.grocery.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OpeningHoursResponse(
        DayOfWeek dayOfWeek,
        @Schema(type = "string", example = "07:00:00") LocalTime opensAt,
        @Schema(type = "string", example = "22:00:00") LocalTime closesAt,
        boolean closed) {
}
