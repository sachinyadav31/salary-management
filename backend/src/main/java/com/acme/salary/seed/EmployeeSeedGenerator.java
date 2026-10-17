package com.acme.salary.seed;

import com.acme.salary.common.Money;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.seed.SeedCatalog.CountryProfile;
import com.acme.salary.seed.SeedCatalog.DepartmentProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * Generates realistic, fully deterministic employees: the same seed and as-of date always
 * produce the same data, so demos and tests are reproducible. Pure Java, no Spring.
 */
public class EmployeeSeedGenerator {

    private static final LocalDate EARLIEST_HIRE = LocalDate.of(2016, 1, 1);
    private static final double INACTIVE_SHARE = 0.03;

    private final Random random;
    private final LocalDate asOf;

    public EmployeeSeedGenerator(long seed, LocalDate asOf) {
        this.random = new Random(seed);
        this.asOf = asOf;
    }

    public List<SeedEmployee> generate(int count) {
        List<SeedEmployee> employees = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            employees.add(next(i));
        }
        return employees;
    }

    private SeedEmployee next(int index) {
        CountryProfile country = weighted(SeedCatalog.COUNTRIES, CountryProfile::weight);
        DepartmentProfile department = weighted(SeedCatalog.DEPARTMENTS, DepartmentProfile::weight);
        JobLevel level = pickLevel();
        String firstName = pick(SeedCatalog.FIRST_NAMES);
        String lastName = pick(SeedCatalog.LAST_NAMES);
        LocalDate hireDate = pickHireDate();
        EmployeeStatus status = random.nextDouble() < INACTIVE_SHARE ? EmployeeStatus.INACTIVE : EmployeeStatus.ACTIVE;

        Money current = currentSalary(country, department, level);
        List<SeedSalaryRecord> history = history(current, hireDate, country);

        return new SeedEmployee(
                "E%05d".formatted(index),
                firstName,
                lastName,
                "%s.%s.%d@acme.example".formatted(firstName, lastName, index).toLowerCase(),
                country.code(),
                department.name(),
                department.titlesByLevel().get(level.ordinal()),
                level,
                hireDate,
                status,
                current,
                history);
    }

    private Money currentSalary(CountryProfile country, DepartmentProfile department, JobLevel level) {
        double noise = 0.85 + random.nextDouble() * 0.30; // ±15%
        long raw = Math.round(SeedCatalog.bandMidpointMinor(country.code(), level) * department.payFactor() * noise);
        return Money.ofMinor(roundTo(raw, roundingStepMinor(country)), country.currency());
    }

    /** Builds history backwards from the current salary: hire record plus up to two yearly raises. */
    private List<SeedSalaryRecord> history(Money current, LocalDate hireDate, CountryProfile country) {
        long yearsEmployed = ChronoUnit.YEARS.between(hireDate, asOf);
        int raises = (int) Math.min(yearsEmployed, random.nextInt(3));
        long step = roundingStepMinor(country);

        List<SeedSalaryRecord> newestFirst = new ArrayList<>();
        long amount = current.minorUnits();
        for (int r = raises; r >= 1; r--) {
            newestFirst.add(new SeedSalaryRecord(Money.ofMinor(amount, country.currency()),
                    hireDate.plusYears(r), pickRaiseReason()));
            double pct = 0.03 + random.nextDouble() * 0.09; // 3–12% raise
            amount = Math.max(step, roundTo(Math.round(amount / (1 + pct)), step));
        }
        newestFirst.add(new SeedSalaryRecord(Money.ofMinor(amount, country.currency()), hireDate, SalaryChangeReason.HIRE));
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private SalaryChangeReason pickRaiseReason() {
        double roll = random.nextDouble();
        if (roll < 0.20) {
            return SalaryChangeReason.PROMOTION;
        }
        if (roll < 0.30) {
            return SalaryChangeReason.MARKET_ADJUSTMENT;
        }
        return SalaryChangeReason.ANNUAL_REVIEW;
    }

    private LocalDate pickHireDate() {
        long span = ChronoUnit.DAYS.between(EARLIEST_HIRE, asOf.minusDays(30));
        return EARLIEST_HIRE.plusDays(random.nextLong(span + 1));
    }

    private JobLevel pickLevel() {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (JobLevel level : JobLevel.values()) {
            cumulative += SeedCatalog.LEVEL_WEIGHTS[level.ordinal()];
            if (roll < cumulative) {
                return level;
            }
        }
        return JobLevel.L1;
    }

    private <T> T weighted(List<T> items, ToIntFunction<T> weight) {
        int total = items.stream().mapToInt(weight).sum();
        int roll = random.nextInt(total);
        for (T item : items) {
            roll -= weight.applyAsInt(item);
            if (roll < 0) {
                return item;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    private static long roundingStepMinor(CountryProfile country) {
        int digits = Money.currency(country.currency()).getDefaultFractionDigits();
        return BigDecimal.valueOf(country.roundToMajor()).movePointRight(digits).longValueExact();
    }

    private static long roundTo(long value, long step) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP).longValueExact() * step;
    }
}
