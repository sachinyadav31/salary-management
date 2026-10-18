package com.acme.salary.employee;

import java.time.LocalDate;

/**
 * Plausible hire dates. A signed offer can start up to a year ahead; anything outside the window is almost certainly a
 * typo in the year (2062 for 2026), which would otherwise silently skew tenure and every date rule built on it.
 */
public final class HireDates {

    static final LocalDate EARLIEST = LocalDate.of(1950, 1, 1);
    static final int MAX_YEARS_AHEAD = 1;

    private HireDates() {
    }

    /** Throws {@link IllegalArgumentException} with a user-facing message. */
    public static void check(LocalDate hireDate, LocalDate today) {
        if (hireDate.isBefore(EARLIEST)) {
            throw new IllegalArgumentException("Hire date cannot be before " + EARLIEST);
        }
        if (hireDate.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
            throw new IllegalArgumentException("Hire date can be at most " + MAX_YEARS_AHEAD + " year ahead");
        }
    }
}
