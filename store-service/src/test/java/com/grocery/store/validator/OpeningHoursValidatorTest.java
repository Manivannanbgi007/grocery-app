package com.grocery.store.validator;

import com.grocery.store.dto.request.OpeningHoursRequest;
import com.grocery.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpeningHoursValidatorTest {

    private final OpeningHoursValidator validator = new OpeningHoursValidator();

    @Test
    void acceptsAValidWeek() {
        assertThatCode(() -> validator.validate(List.of(
                open(DayOfWeek.MONDAY, 7, 22),
                new OpeningHoursRequest(DayOfWeek.SUNDAY, null, null, true))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheSameDayTwice() {
        assertThatThrownBy(() -> validator.validate(List.of(open(DayOfWeek.MONDAY, 7, 22), open(DayOfWeek.MONDAY, 8, 20))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MONDAY appears more than once");
    }

    @Test
    void rejectsAnOpenDayWithoutTimes() {
        assertThatThrownBy(() -> validator.validate(List.of(new OpeningHoursRequest(DayOfWeek.TUESDAY, null, null, false))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsClosingBeforeOpening() {
        assertThatThrownBy(() -> validator.validate(List.of(open(DayOfWeek.FRIDAY, 22, 7))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("FRIDAY: opensAt must be before closesAt");
    }

    private OpeningHoursRequest open(DayOfWeek day, int opensAtHour, int closesAtHour) {
        return new OpeningHoursRequest(day, LocalTime.of(opensAtHour, 0), LocalTime.of(closesAtHour, 0), false);
    }
}
