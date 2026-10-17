package com.acme.salary.bulk;

import com.acme.salary.bonus.BonusRules;
import com.acme.salary.bonus.BonusType;
import com.acme.salary.common.Money;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides, per selected employee, what a bulk change does or why they are skipped. Pure: it reuses the single-change
 * rules ({@link SalaryHistory#checkCanAdd}, {@link BonusRules#check}), so a bulk change can never do anything a single
 * change couldn't.
 */
public final class BulkPlanner {

    public enum Kind { RAISE, BONUS }

    static final BigDecimal MAX_RAISE_PERCENT = BigDecimal.valueOf(50);
    static final BigDecimal MAX_BONUS_PERCENT = BigDecimal.valueOf(100);

    /** One selected employee with what the rules need. Band bounds are null when no band exists. */
    public record Target(long employeeId, String employeeCode, String name, boolean active, LocalDate hireDate,
                         Money currentSalary, Money bandMin, Money bandMax, SalaryHistory history) {
    }

    /**
     * {@code percent} is the raise % (RAISE) or the bonus as % of base salary (BONUS); {@code fixedAmount} is a bonus
     * in the employees' one currency (BONUS only, instead of percent).
     */
    public record Request(Kind kind, BigDecimal percent, BigDecimal fixedAmount, LocalDate date,
                          SalaryChangeReason reason, BonusType bonusType, String note) {
    }

    /**
     * {@code from} is the salary in effect on the date the raise applies to; {@code currentAfter} is what the employee
     * is paid today once it's saved (unchanged for a scheduled raise or one superseded by a later change).
     */
    public record PlannedRaise(Target target, Money from, Money to, Money currentAfter, BandPosition before,
                               BandPosition after) {

        public boolean changesCurrentSalary() {
            return !currentAfter.equals(target.currentSalary());
        }
    }

    public record PlannedBonus(Target target, Money amount) {
    }

    /** {@code reason} is the same text for everyone skipped for the same cause, so the UI can group them. */
    public record Skipped(Target target, String reason) {
    }

    public record Plan(List<PlannedRaise> raises, List<PlannedBonus> bonuses, List<Skipped> skipped) {

        public int affected() {
            return raises.size() + bonuses.size();
        }
    }

    private BulkPlanner() {
    }

    /** Throws {@link IllegalArgumentException} if the request itself is invalid (it would fail for everyone). */
    public static Plan plan(List<Target> targets, Request request, LocalDate today) {
        validate(request, targets, today);
        List<PlannedRaise> raises = new ArrayList<>();
        List<PlannedBonus> bonuses = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (Target target : targets) {
            if (!target.active()) {
                skipped.add(new Skipped(target, "Inactive"));
            } else if (request.date().isBefore(target.hireDate())) {
                skipped.add(new Skipped(target, "Joined after " + request.date()));
            } else {
                try {
                    if (request.kind() == Kind.RAISE) {
                        raises.add(planRaise(target, request, today));
                    } else {
                        bonuses.add(planBonus(target, request, today));
                    }
                } catch (IllegalArgumentException e) {
                    skipped.add(new Skipped(target, e.getMessage()));
                }
            }
        }
        return new Plan(raises, bonuses, skipped);
    }

    private static PlannedRaise planRaise(Target target, Request request, LocalDate today) {
        // A raise applies to the salary in effect on its date, so it stacks on an earlier scheduled change.
        SalaryHistory.Entry base = target.history().currentAsOf(request.date())
                .orElseThrow(() -> new IllegalArgumentException("No salary in effect on " + request.date()));
        Money raised = roundToWholeUnits(base.amount(), BigDecimal.ONE.add(request.percent().movePointLeft(2)));
        target.history().checkCanAdd(new SalaryHistory.NewChange(raised, request.date(), request.reason(), request.note()), today);

        // It becomes today's salary only if it's already effective and no later change has taken over since.
        boolean supersededOrScheduled = request.date().isAfter(today) || target.history().currentAsOf(today)
                .map(current -> current.effectiveDate().isAfter(request.date())).orElse(false);
        Money currentAfter = supersededOrScheduled ? target.currentSalary() : raised;
        return new PlannedRaise(target, base.amount(), raised, currentAfter,
                position(target, target.currentSalary()), position(target, raised));
    }

    private static PlannedBonus planBonus(Target target, Request request, LocalDate today) {
        Money amount = request.fixedAmount() != null
                ? Money.of(request.fixedAmount(), target.currentSalary().currencyCode())
                : roundToWholeUnits(target.currentSalary(), request.percent().movePointLeft(2));
        BonusRules.check(new BonusRules.NewBonus(request.bonusType(), amount, request.date(), request.note()),
                target.currentSalary().currencyCode(), target.hireDate(), today);
        return new PlannedBonus(target, amount);
    }

    private static void validate(Request request, List<Target> targets, LocalDate today) {
        if (request.kind() == null) {
            throw new IllegalArgumentException("Choose a raise or a bonus");
        }
        if (request.date() == null) {
            throw new IllegalArgumentException("Choose the date it takes effect");
        }
        if (request.date().isAfter(today.plusYears(1))) {
            throw new IllegalArgumentException("The date can be at most 1 year ahead");
        }
        if (request.kind() == Kind.RAISE) {
            if (!between(request.percent(), MAX_RAISE_PERCENT)) {
                throw new IllegalArgumentException("Raise must be more than 0% and at most " + MAX_RAISE_PERCENT + "%");
            }
            if (request.reason() != SalaryChangeReason.ANNUAL_REVIEW && request.reason() != SalaryChangeReason.MERIT
                    && request.reason() != SalaryChangeReason.MARKET_ADJUSTMENT) {
                // Promotions change a level and corrections fix one record: both are decisions about one person.
                throw new IllegalArgumentException("A bulk raise is an annual review, a merit raise or a market adjustment");
            }
            return;
        }
        if (request.bonusType() == null) {
            throw new IllegalArgumentException("Choose a bonus type");
        }
        if (request.bonusType() == BonusType.SIGN_ON) {
            throw new IllegalArgumentException("Sign-on bonuses are awarded one person at a time");
        }
        if ((request.percent() == null) == (request.fixedAmount() == null)) {
            throw new IllegalArgumentException("Give the bonus either as a % of base salary or as a fixed amount");
        }
        if (request.percent() != null && !between(request.percent(), MAX_BONUS_PERCENT)) {
            throw new IllegalArgumentException("Bonus must be more than 0% and at most " + MAX_BONUS_PERCENT + "% of base salary");
        }
        if (request.fixedAmount() != null
                && targets.stream().map(t -> t.currentSalary().currencyCode()).distinct().count() > 1) {
            throw new IllegalArgumentException(
                    "A fixed amount needs one currency: filter to a single country, or give a % of base salary");
        }
    }

    private static boolean between(BigDecimal value, BigDecimal max) {
        return value != null && value.signum() > 0 && value.compareTo(max) <= 0;
    }

    /** Multiplies and rounds half up to whole currency units (₹12,95,000, ¥7,140,000), so pay stays a round number. */
    static Money roundToWholeUnits(Money base, BigDecimal factor) {
        int digits = base.currency().getDefaultFractionDigits();
        BigDecimal major = base.toDecimal().multiply(factor).setScale(0, RoundingMode.HALF_UP);
        return Money.ofMinor(major.movePointRight(digits).longValueExact(), base.currencyCode());
    }

    private static BandPosition position(Target target, Money salary) {
        if (target.bandMin() == null || target.bandMax() == null) {
            return null;
        }
        return salary.minorUnits() < target.bandMin().minorUnits() ? BandPosition.BELOW
                : salary.minorUnits() > target.bandMax().minorUnits() ? BandPosition.ABOVE : BandPosition.WITHIN;
    }
}
