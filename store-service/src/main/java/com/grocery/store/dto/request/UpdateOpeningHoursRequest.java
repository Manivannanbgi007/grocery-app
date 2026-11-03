package com.grocery.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces the whole week. Days that are not in the list are treated as closed.
 */
public record UpdateOpeningHoursRequest(

        @Schema(description = "One entry per day (at most 7)")
        @NotEmpty @Size(max = 7)
        List<@Valid OpeningHoursRequest> days) {
}
