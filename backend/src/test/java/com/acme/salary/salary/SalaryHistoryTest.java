package com.acme.salary.salary;

import com.acme.salary.common.Money;
import com.acme.salary.salary.SalaryHistory.Entry;
import com.acme.salary.salary.SalaryHistory.EntryStatus;
import com.acme.salary.salary.SalaryHistory.NewChange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.acme.salary.salary.SalaryChangeReason.ANNUAL_REVIEW;
import static com.acme.salary.salary.SalaryChangeReason.CORRECTION;
import static com.acme.salary.salary.SalaryChangeReason.HIRE;
import static com.acme.salary.salary.SalaryChangeReason.PROMOTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalaryHistoryTest {

    private static final LocalDate HIRED = LocalDate.of(2022, 4, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private final SalaryHistory history = new SalaryHistory(HIRED, List.of(
            entry(1, "50000", HIRED, HIRE),
            entry(2, "55000", LocalDate.of(2023, 4, 1), ANNUAL_REVIEW),
            entry(3, "66000", LocalDate.of(2027, 1, 1), PROMOTION)));   // scheduled

    // --- resolving the current salary ---

    @Test
    void currentSalaryIsTheLatestRecordInEffect() {
        assertThat(history.currentAsOf(TODAY).orElseThrow().amount()).isEqualTo(gbp("55000"));
    }

    @Test
    void scheduledChangesTakeEffectOnTheirDate() {
        assertThat(history.currentAsOf(LocalDate.of(2026, 12, 31)).orElseThrow().amount()).isEqualTo(gbp("55000"));
        assertThat(history.currentAsOf(LocalDate.of(2027, 1, 1)).orElseThrow().amount()).isEqualTo(gbp("66000"));
    }

    @Test
    void nothingIsInEffectBeforeHire() {
        assertThat(history.currentAsOf(HIRED.minusDays(1))).isEmpty();
    }

    @Test
    void onTheSameDateTheMostRecentlyRecordedEntryWins() {
        SalaryHistory corrected = new SalaryHistory(HIRED, List.of(
                entry(1, "50000", HIRED, HIRE),
                entry(7, "52000", HIRED, CORRECTION)));

        assertThat(corrected.currentAsOf(TODAY).orElseThrow().amount()).isEqualTo(gbp("52000"));
    }

    // --- timeline view ---

    @Test
    void timelineIsNewestFirstWithStatusAndPercentChange() {
        var timeline = history.timeline(TODAY);

        assertThat(timeline).extracting(t -> t.entry().sequence()).containsExactly(3L, 2L, 1L);
        assertThat(timeline).extracting(SalaryHistory.TimelineEntry::status)
                .containsExactly(EntryStatus.SCHEDULED, EntryStatus.CURRENT, EntryStatus.PAST);
        assertThat(timeline.get(0).changePercent()).isEqualByComparingTo("20.0");   // 55k -> 66k
        assertThat(timeline.get(1).changePercent()).isEqualByComparingTo("10.0");   // 50k -> 55k
        assertThat(timeline.get(2).changePercent()).isNull();                        // hire
    }

    // --- rules for new changes ---

    @Test
    void acceptsAValidRaise() {
        history.checkCanAdd(new NewChange(gbp("60000"), TODAY, ANNUAL_REVIEW, null), TODAY);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> history.checkCanAdd(new NewChange(gbp("0"), TODAY, ANNUAL_REVIEW, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
    }

    @Test
    void rejectsDatesBeforeHire() {
        assertThatThrownBy(() -> history.checkCanAdd(
                new NewChange(gbp("60000"), HIRED.minusDays(1), ANNUAL_REVIEW, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hire");
    }

    @Test
    void rejectsDatesMoreThanAYearAhead() {
        history.checkCanAdd(new NewChange(gbp("60000"), TODAY.plusYears(1), ANNUAL_REVIEW, null), TODAY);
        assertThatThrownBy(() -> history.checkCanAdd(
                new NewChange(gbp("60000"), TODAY.plusYears(1).plusDays(1), ANNUAL_REVIEW, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("year");
    }

    @Test
    void hireCannotBeRecordedAsAChange() {
        assertThatThrownBy(() -> history.checkCanAdd(new NewChange(gbp("60000"), TODAY, HIRE, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HIRE");
    }

    @Test
    void sameDateNeedsACorrection() {
        LocalDate taken = LocalDate.of(2023, 4, 1);
        assertThatThrownBy(() -> history.checkCanAdd(new NewChange(gbp("56000"), taken, PROMOTION, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CORRECTION");

        history.checkCanAdd(new NewChange(gbp("56000"), taken, CORRECTION, "Typo in review letter"), TODAY);
    }

    @Test
    void correctionsMustExplainWhy() {
        assertThatThrownBy(() -> history.checkCanAdd(new NewChange(gbp("56000"), TODAY, CORRECTION, " "), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("note");
    }

    @Test
    void currencyMustMatchTheExistingHistory() {
        assertThatThrownBy(() -> history.checkCanAdd(
                new NewChange(Money.ofMinor(100, "USD"), TODAY, ANNUAL_REVIEW, null), TODAY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GBP");
    }

    private static Entry entry(long sequence, String amount, LocalDate date, SalaryChangeReason reason) {
        return new Entry(sequence, gbp(amount), date, reason, null, null);
    }

    private static Money gbp(String amount) {
        return Money.of(new java.math.BigDecimal(amount), "GBP");
    }
}
