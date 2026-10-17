package com.acme.salary.seed;

import com.acme.salary.common.Money;
import com.acme.salary.bonus.BonusType;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.VariableTarget;
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
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Generates realistic, fully deterministic employees: the same seed and as-of date always
 * produce the same data, so demos and tests are reproducible. Pure Java, no Spring.
 */
public class EmployeeSeedGenerator {

    private static final LocalDate EARLIEST_HIRE = LocalDate.of(2016, 1, 1);
    private static final double INACTIVE_SHARE = 0.03;

    /** ACME's annual review cycle: raises take effect on 1 April. */
    static final int REVIEW_CYCLE_MONTH = 4;
    static final int MIN_MONTHS_BEFORE_REVIEW = 6;
    static final double ANNUAL_RAISE_SHARE = 0.85;
    static final double PROMOTION_SHARE = 0.25;
    static final double MARKET_ADJUSTMENT_SHARE = 0.05;
    static final double SIGN_ON_SHARE = 0.20;
    static final double NO_PAYOUT_SHARE = 0.10;
    static final double SPOT_SHARE = 0.08;
    private static final List<String> SPOT_REASONS = List.of(
            "Shipped a critical launch ahead of schedule", "Covered for the team during an incident",
            "Outstanding customer feedback", "Mentored three new joiners", "Closed a strategic deal");

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
        List<SeedBonus> bonuses = bonuses(current, hireDate, status, department.name(), level, country);

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
                history,
                bonuses);
    }

    private Money currentSalary(CountryProfile country, DepartmentProfile department, JobLevel level) {
        double noise = 0.85 + random.nextDouble() * 0.30; // ±15%
        long raw = Math.round(SeedCatalog.bandMidpointMinor(country.code(), level) * department.payFactor() * noise);
        return Money.ofMinor(roundTo(raw, roundingStepMinor(country)), country.currency());
    }

    /**
     * Builds history backwards from the current salary, so the latest record always equals it:
     * <ul>
     *   <li>ACME runs one review cycle: annual reviews take effect on 1 April for everyone employed at least
     *       6 months by then; most get a 2–8% raise, some none that year.</li>
     *   <li>Some people are promoted on another date (8–15%); a few get a market adjustment (4–10%).</li>
     * </ul>
     */
    private List<SeedSalaryRecord> history(Money current, LocalDate hireDate, CountryProfile country) {
        record Change(SalaryChangeReason reason, double raise) {
        }
        // Chronological change events, at most one per date.
        TreeMap<LocalDate, Change> changes = new TreeMap<>();
        for (int year = hireDate.getYear(); year <= asOf.getYear(); year++) {
            LocalDate cycle = LocalDate.of(year, REVIEW_CYCLE_MONTH, 1);
            boolean eligible = !cycle.isBefore(hireDate.plusMonths(MIN_MONTHS_BEFORE_REVIEW)) && !cycle.isAfter(asOf);
            if (eligible && random.nextDouble() < ANNUAL_RAISE_SHARE) {
                changes.put(cycle, new Change(SalaryChangeReason.ANNUAL_REVIEW, 0.02 + random.nextDouble() * 0.06));
            }
        }
        long tenureDays = ChronoUnit.DAYS.between(hireDate, asOf);
        if (tenureDays >= 548 && random.nextDouble() < PROMOTION_SHARE) {
            changes.putIfAbsent(offCycle(hireDate.plusDays(365 + random.nextLong(tenureDays - 365))),
                    new Change(SalaryChangeReason.PROMOTION, 0.08 + random.nextDouble() * 0.07));
        }
        if (tenureDays >= 180 && random.nextDouble() < MARKET_ADJUSTMENT_SHARE) {
            changes.putIfAbsent(offCycle(hireDate.plusDays(90 + random.nextLong(tenureDays - 90))),
                    new Change(SalaryChangeReason.MARKET_ADJUSTMENT, 0.04 + random.nextDouble() * 0.06));
        }

        long step = roundingStepMinor(country);
        List<SeedSalaryRecord> newestFirst = new ArrayList<>();
        long amount = current.minorUnits();
        for (var change : changes.descendingMap().entrySet()) {
            newestFirst.add(new SeedSalaryRecord(Money.ofMinor(amount, country.currency()), change.getKey(),
                    change.getValue().reason()));
            amount = Math.max(step, roundTo(Math.round(amount / (1 + change.getValue().raise())), step));
        }
        newestFirst.add(new SeedSalaryRecord(Money.ofMinor(amount, country.currency()), hireDate, SalaryChangeReason.HIRE));
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    /**
     * One-off awards: performance payouts of variable pay on 1 April for each full year worked (10% get nothing that
     * year; the rest 60–140% of target), sign-on bonuses for some recent hires, and spot bonuses for ~8% of active
     * people in the last year, each with the reason it was given.
     */
    private List<SeedBonus> bonuses(Money current, LocalDate hireDate, EmployeeStatus status, String department,
                                    JobLevel level, CountryProfile country) {
        List<SeedBonus> awards = new ArrayList<>();
        long step = roundingStepMinor(country);
        String currency = country.currency();
        if (!hireDate.isBefore(asOf.minusYears(2)) && random.nextDouble() < SIGN_ON_SHARE) {
            awards.add(new SeedBonus(BonusType.SIGN_ON, percentOf(current, 0.05 + random.nextDouble() * 0.05, step),
                    hireDate, null));
        }
        long target = VariableTarget.defaultFor(department, level).of(current).minorUnits();
        for (int year = asOf.getYear() - 1; year <= asOf.getYear(); year++) {
            LocalDate payout = LocalDate.of(year, REVIEW_CYCLE_MONTH, 1);
            boolean workedFullYear = !hireDate.isAfter(payout.minusYears(1));
            if (payout.isAfter(asOf) || !workedFullYear || random.nextDouble() < NO_PAYOUT_SHARE) {
                continue;
            }
            long amount = roundTo(Math.round(target * (0.6 + random.nextDouble() * 0.8)), step);
            if (amount > 0) {
                awards.add(new SeedBonus(BonusType.PERFORMANCE, Money.ofMinor(amount, currency), payout, null));
            }
        }
        if (status == EmployeeStatus.ACTIVE && random.nextDouble() < SPOT_SHARE) {
            LocalDate on = asOf.minusDays(random.nextLong(365));
            awards.add(new SeedBonus(BonusType.SPOT, percentOf(current, 0.02 + random.nextDouble() * 0.03, step),
                    on.isBefore(hireDate) ? hireDate : on, pick(SPOT_REASONS)));
        }
        awards.sort(java.util.Comparator.comparing(SeedBonus::awardedOn));
        return List.copyOf(awards);
    }

    private static Money percentOf(Money salary, double share, long step) {
        return Money.ofMinor(Math.max(step, roundTo(Math.round(salary.minorUnits() * share), step)), salary.currencyCode());
    }

    /** Promotions and market adjustments happen outside the review cycle date. */
    private static LocalDate offCycle(LocalDate date) {
        return date.getMonthValue() == REVIEW_CYCLE_MONTH && date.getDayOfMonth() == 1 ? date.plusDays(1) : date;
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
