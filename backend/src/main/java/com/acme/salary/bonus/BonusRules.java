package com.acme.salary.bonus;

import com.acme.salary.common.Money;

import java.time.LocalDate;

/** Rules for a new bonus award. Pure; throws IllegalArgumentException with a user-facing message. */
public final class BonusRules {

    static final int MAX_YEARS_AHEAD = 1;

    public record NewBonus(BonusType type, Money amount, LocalDate awardedOn, String note) {
    }

    private BonusRules() {
    }

    public static void check(NewBonus bonus, String employeeCurrency, LocalDate hireDate, LocalDate today) {
        if (!bonus.amount().isPositive()) {
            throw new IllegalArgumentException("Bonus amount must be positive");
        }
        if (!bonus.amount().currencyCode().equals(employeeCurrency)) {
            throw new IllegalArgumentException("Bonus must be in " + employeeCurrency);
        }
        if (bonus.awardedOn().isBefore(hireDate)) {
            throw new IllegalArgumentException("Award date cannot be before the hire date " + hireDate);
        }
        if (bonus.awardedOn().isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
            throw new IllegalArgumentException("Award date can be at most " + MAX_YEARS_AHEAD + " year ahead");
        }
        if (bonus.type() == BonusType.SPOT && (bonus.note() == null || bonus.note().isBlank())) {
            throw new IllegalArgumentException("A spot bonus needs a note saying what it recognises");
        }
    }
}
