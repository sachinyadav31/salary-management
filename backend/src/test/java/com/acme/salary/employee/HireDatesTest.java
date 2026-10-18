package com.acme.salary.employee;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HireDatesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @Test
    void acceptsPastDatesAndSignedOffersUpToAYearAhead() {
        assertThatCode(() -> HireDates.check(LocalDate.of(2003, 6, 2), TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> HireDates.check(TODAY, TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> HireDates.check(TODAY.plusYears(1), TODAY)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTyposInTheYear() {
        assertThatThrownBy(() -> HireDates.check(TODAY.plusYears(1).plusDays(1), TODAY))
                .hasMessage("Hire date can be at most 1 year ahead");
        assertThatThrownBy(() -> HireDates.check(LocalDate.of(1949, 12, 31), TODAY))
                .hasMessage("Hire date cannot be before 1950-01-01");
    }
}
