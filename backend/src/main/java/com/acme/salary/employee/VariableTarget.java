package com.acme.salary.employee;

import com.acme.salary.common.Money;
import com.acme.salary.reference.JobLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Variable pay target (incentive/bonus target) as basis points of base salary: 1000 = 10%.
 * Integer basis points, consistent with "no floating point for money". Pure value object.
 */
public record VariableTarget(int basisPoints) {

    public static final VariableTarget NONE = new VariableTarget(0);

    public VariableTarget {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Variable pay target must be between 0% and 100%");
        }
    }

    /** From a percentage such as 12.5; at most two decimals (0.01% = 1 basis point). */
    public static VariableTarget ofPercent(BigDecimal percent) {
        if (percent.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Variable pay target allows at most two decimal places");
        }
        return new VariableTarget(percent.movePointRight(2).intValueExact());
    }

    /**
     * ACME's default target by role: Sales is commission-heavy (20%); others grow with seniority.
     * Used for seed data and new hires; HR can change it per person.
     */
    public static VariableTarget defaultFor(String department, JobLevel level) {
        if ("Sales".equals(department)) {
            return new VariableTarget(2_000);
        }
        return new VariableTarget(switch (level) {
            case L1, L2, L3 -> 1_000;
            case L4 -> 1_200;
            case L5 -> 1_500;
            case L6 -> 2_000;
        });
    }

    public BigDecimal percent() {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros();
    }

    /** Target amount for a base salary, rounded half up to the currency's minor unit. */
    public Money of(Money base) {
        long minor = BigDecimal.valueOf(base.minorUnits()).multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        return Money.ofMinor(minor, base.currencyCode());
    }

    /** Display form for history, e.g. "12.5%". */
    public String label() {
        return percent().toPlainString() + "%";
    }
}
