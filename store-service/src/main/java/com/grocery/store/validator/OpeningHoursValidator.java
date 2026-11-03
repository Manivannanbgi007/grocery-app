package com.grocery.store.validator;

import com.grocery.store.dto.request.OpeningHoursRequest;
import com.grocery.store.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a week of opening hours. These rules compare several fields, so they cannot be
 * expressed with simple annotations on the request.
 */
@Component
public class OpeningHoursValidator {

    public void validate(List<OpeningHoursRequest> days) {
        Set<DayOfWeek> seenDays = EnumSet.noneOf(DayOfWeek.class);

        for (OpeningHoursRequest day : days) {
            if (!seenDays.add(day.dayOfWeek())) {
                throw new BusinessException(day.dayOfWeek() + " appears more than once");
            }
            if (day.closedAllDay()) {
                continue;
            }
            if (day.opensAt() == null || day.closesAt() == null) {
                throw new BusinessException(day.dayOfWeek() + ": opensAt and closesAt are required when the store is open");
            }
            if (!day.opensAt().isBefore(day.closesAt())) {
                throw new BusinessException(day.dayOfWeek() + ": opensAt must be before closesAt");
            }
        }
    }
}
