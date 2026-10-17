package com.acme.salary.bulk;

import com.acme.salary.bonus.BonusType;
import com.acme.salary.bulk.BulkPlanner.Kind;
import com.acme.salary.bulk.BulkPlanner.Plan;
import com.acme.salary.bulk.BulkPlanner.Request;
import com.acme.salary.bulk.BulkPlanner.Target;
import com.acme.salary.common.Money;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.salary.SalaryChangeReason;
import com.acme.salary.salary.SalaryHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkPlannerTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    static final LocalDate HIRED = LocalDate.of(2023, 3, 1);

    static Target target(long id, String salary, String currency, boolean active, LocalDate hired, Object... changes) {
        Money current = Money.of(new BigDecimal(salary), currency);
        List<SalaryHistory.Entry> entries = new java.util.ArrayList<>();
        entries.add(new SalaryHistory.Entry(0, current, hired, SalaryChangeReason.HIRE, null, null));
        for (int i = 0; i < changes.length; i += 2) {
            entries.add(new SalaryHistory.Entry(i + 1, Money.of(new BigDecimal((String) changes[i + 1]), currency),
                    (LocalDate) changes[i], SalaryChangeReason.ANNUAL_REVIEW, null, null));
        }
        return new Target(id, "E" + id, "Person " + id, active, hired, current,
                Money.of(new BigDecimal("50000"), currency), Money.of(new BigDecimal("80000"), currency),
                new SalaryHistory(hired, entries));
    }

    static Request raise(String percent, LocalDate date) {
        return new Request(Kind.RAISE, new BigDecimal(percent), null, date, SalaryChangeReason.MERIT, null, null);
    }

    @Test
    void raisesRoundToWholeCurrencyUnitsAndReportBandMoves() {
        Plan plan = BulkPlanner.plan(List.of(target(1, "48123.40", "GBP", true, HIRED)), raise("4.5", TODAY), TODAY);

        var r = plan.raises().getFirst();
        assertThat(r.to()).isEqualTo(Money.of(new BigDecimal("50289"), "GBP")); // 50,288.95 → 50,289
        assertThat(r.currentAfter()).isEqualTo(r.to());
        assertThat(r.before()).isEqualTo(BandPosition.BELOW);
        assertThat(r.after()).isEqualTo(BandPosition.WITHIN);
    }

    @Test
    void yenHasNoMinorUnit() {
        assertThat(BulkPlanner.roundToWholeUnits(Money.of(new BigDecimal("7000001"), "JPY"), new BigDecimal("1.03")))
                .isEqualTo(Money.of(new BigDecimal("7210001"), "JPY"));
    }

    @Test
    void skipsWithOneReasonPerCauseSoTheUiCanGroupThem() {
        LocalDate april = LocalDate.of(2027, 4, 1);
        Plan plan = BulkPlanner.plan(List.of(
                target(1, "60000", "GBP", true, HIRED),
                target(2, "60000", "GBP", false, HIRED),
                target(3, "60000", "GBP", true, LocalDate.of(2027, 5, 1)),
                target(4, "60000", "GBP", true, LocalDate.of(2027, 6, 1)),
                target(5, "60000", "GBP", true, HIRED, april, "63000")), raise("3", april), TODAY);

        assertThat(plan.raises()).extracting(r -> r.target().employeeId()).containsExactly(1L);
        assertThat(plan.skipped()).extracting(s -> s.target().employeeId() + ": " + s.reason()).containsExactly(
                "2: Inactive",
                "3: Joined after 2027-04-01",
                "4: Joined after 2027-04-01",
                "5: A change already exists on 2027-04-01; record a CORRECTION to override it");
    }

    @Test
    void aScheduledRaiseKeepsTodaysSalaryAndStacksOnAnEarlierScheduledChange() {
        Target t = target(1, "60000", "GBP", true, HIRED, LocalDate.of(2027, 1, 1), "62000");

        var r = BulkPlanner.plan(List.of(t), raise("10", LocalDate.of(2027, 4, 1)), TODAY).raises().getFirst();

        assertThat(r.from()).isEqualTo(Money.of(new BigDecimal("62000"), "GBP"));
        assertThat(r.to()).isEqualTo(Money.of(new BigDecimal("68200"), "GBP"));
        assertThat(r.currentAfter()).isEqualTo(t.currentSalary());
        assertThat(r.changesCurrentSalary()).isFalse();
    }

    @Test
    void aBackDatedRaiseSupersededByALaterChangeDoesNotChangeTodaysSalary() {
        Target t = target(1, "66000", "GBP", true, HIRED, LocalDate.of(2026, 4, 1), "66000");

        var r = BulkPlanner.plan(List.of(t), raise("5", LocalDate.of(2025, 4, 1)), TODAY).raises().getFirst();

        assertThat(r.currentAfter()).isEqualTo(t.currentSalary());
    }

    @Test
    void bonusesAsPercentOfBaseOrAFixedAmountInOneCurrency() {
        Request percent = new Request(Kind.BONUS, new BigDecimal("10"), null, TODAY, null, BonusType.PERFORMANCE, null);
        assertThat(BulkPlanner.plan(List.of(target(1, "60000.50", "GBP", true, HIRED)), percent, TODAY).bonuses().getFirst().amount())
                .isEqualTo(Money.of(new BigDecimal("6000"), "GBP"));

        Request fixed = new Request(Kind.BONUS, null, new BigDecimal("1000"), TODAY, null, BonusType.SPOT, "Launch");
        assertThat(BulkPlanner.plan(List.of(target(1, "60000", "GBP", true, HIRED)), fixed, TODAY).bonuses().getFirst().amount())
                .isEqualTo(Money.of(new BigDecimal("1000"), "GBP"));
        assertThatThrownBy(() -> BulkPlanner.plan(
                List.of(target(1, "60000", "GBP", true, HIRED), target(2, "7000000", "JPY", true, HIRED)), fixed, TODAY))
                .hasMessageContaining("one currency");
    }

    @Test
    void rejectsRequestsThatWouldFailForEveryone() {
        List<Target> one = List.of(target(1, "60000", "GBP", true, HIRED));
        assertThatThrownBy(() -> BulkPlanner.plan(one, raise("0", TODAY), TODAY)).hasMessageContaining("more than 0%");
        assertThatThrownBy(() -> BulkPlanner.plan(one, raise("51", TODAY), TODAY)).hasMessageContaining("at most 50%");
        assertThatThrownBy(() -> BulkPlanner.plan(one, new Request(Kind.RAISE, BigDecimal.ONE, null, TODAY,
                SalaryChangeReason.PROMOTION, null, null), TODAY)).hasMessageContaining("annual review");
        assertThatThrownBy(() -> BulkPlanner.plan(one, raise("3", TODAY.plusYears(1).plusDays(1)), TODAY))
                .hasMessageContaining("1 year ahead");
        assertThatThrownBy(() -> BulkPlanner.plan(one, new Request(Kind.BONUS, BigDecimal.ONE, BigDecimal.TEN, TODAY,
                null, BonusType.PERFORMANCE, null), TODAY)).hasMessageContaining("either");
        assertThatThrownBy(() -> BulkPlanner.plan(one, new Request(Kind.BONUS, BigDecimal.ONE, null, TODAY,
                null, BonusType.SIGN_ON, null), TODAY)).hasMessageContaining("one person at a time");
    }

    @Test
    void aSpotBonusWithoutANoteIsSkippedByTheSingleAwardRule() {
        Request spot = new Request(Kind.BONUS, null, new BigDecimal("500"), TODAY, null, BonusType.SPOT, null);
        assertThat(BulkPlanner.plan(List.of(target(1, "60000", "GBP", true, HIRED)), spot, TODAY).skipped())
                .extracting(BulkPlanner.Skipped::reason).containsExactly("A spot bonus needs a note saying what it recognises");
    }
}
