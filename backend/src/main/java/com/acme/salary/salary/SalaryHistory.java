package com.acme.salary.salary;

import com.acme.salary.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An employee's append-only salary history and the rules for adding to it. Pure Java, no Spring/JPA.
 * <p>
 * Ordering: by effective date, then by recording order ({@code sequence}). On the same date the most
 * recently recorded entry wins, which is how corrections override an earlier record.
 */
public final class SalaryHistory {

    /** {@code recordedBy} is the user's name, or null for system-generated history. */
    public record Entry(long sequence, Money amount, LocalDate effectiveDate, SalaryChangeReason reason, String note,
                        String recordedBy) {
    }

    public record NewChange(Money amount, LocalDate effectiveDate, SalaryChangeReason reason, String note) {
    }

    public enum EntryStatus { PAST, CURRENT, SCHEDULED }

    /** {@code changePercent} is relative to the previous entry, rounded to one decimal; null for the first. */
    public record TimelineEntry(Entry entry, EntryStatus status, BigDecimal changePercent) {
    }

    static final int MAX_YEARS_AHEAD = 1;

    private static final Comparator<Entry> CHRONOLOGICAL =
            Comparator.comparing(Entry::effectiveDate).thenComparingLong(Entry::sequence);

    private final LocalDate hireDate;
    private final List<Entry> chronological;

    public SalaryHistory(LocalDate hireDate, List<Entry> entries) {
        this.hireDate = hireDate;
        this.chronological = entries.stream().sorted(CHRONOLOGICAL).toList();
    }

    /** The entry in effect on {@code date}: the latest one whose effective date is on or before it. */
    public Optional<Entry> currentAsOf(LocalDate date) {
        Entry current = null;
        for (Entry e : chronological) {
            if (e.effectiveDate().isAfter(date)) {
                break;
            }
            current = e;
        }
        return Optional.ofNullable(current);
    }

    /** Newest first, each with its status relative to {@code today} and % change from the previous entry. */
    public List<TimelineEntry> timeline(LocalDate today) {
        Entry current = currentAsOf(today).orElse(null);
        List<TimelineEntry> result = new ArrayList<>(chronological.size());
        Entry previous = null;
        for (Entry e : chronological) {
            EntryStatus status = e == current ? EntryStatus.CURRENT
                    : e.effectiveDate().isAfter(today) ? EntryStatus.SCHEDULED : EntryStatus.PAST;
            result.add(new TimelineEntry(e, status, previous == null ? null : percentChange(previous, e)));
            previous = e;
        }
        return result.reversed();
    }

    /** Throws {@link IllegalArgumentException} with a user-facing message if the change breaks a rule. */
    public void checkCanAdd(NewChange change, LocalDate today) {
        if (change.reason() == SalaryChangeReason.HIRE) {
            throw new IllegalArgumentException("HIRE is recorded automatically when the employee is created");
        }
        if (!change.amount().isPositive()) {
            throw new IllegalArgumentException("Salary must be positive");
        }
        if (!chronological.isEmpty()
                && !chronological.getFirst().amount().currencyCode().equals(change.amount().currencyCode())) {
            throw new IllegalArgumentException("Salary must be in "
                    + chronological.getFirst().amount().currencyCode());
        }
        if (change.effectiveDate().isBefore(hireDate)) {
            throw new IllegalArgumentException("Effective date cannot be before the hire date " + hireDate);
        }
        if (change.effectiveDate().isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
            throw new IllegalArgumentException("Effective date can be at most " + MAX_YEARS_AHEAD + " year ahead");
        }
        boolean dateTaken = chronological.stream().anyMatch(e -> e.effectiveDate().equals(change.effectiveDate()));
        if (dateTaken && change.reason() != SalaryChangeReason.CORRECTION) {
            throw new IllegalArgumentException(
                    "A change already exists on " + change.effectiveDate() + "; record a CORRECTION to override it");
        }
        if (change.reason() == SalaryChangeReason.CORRECTION && (change.note() == null || change.note().isBlank())) {
            throw new IllegalArgumentException("A CORRECTION needs a note explaining why");
        }
    }

    private static BigDecimal percentChange(Entry from, Entry to) {
        BigDecimal before = BigDecimal.valueOf(from.amount().minorUnits());
        BigDecimal after = BigDecimal.valueOf(to.amount().minorUnits());
        return after.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }
}
