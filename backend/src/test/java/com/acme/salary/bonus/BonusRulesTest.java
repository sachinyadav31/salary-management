package com.acme.salary.bonus;

import com.acme.salary.bonus.BonusRules.NewBonus;
import com.acme.salary.common.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BonusRulesTest {

    private static final LocalDate HIRED = LocalDate.of(2024, 1, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private static void check(BonusType type, long minor, String currency, LocalDate on, String note) {
        BonusRules.check(new NewBonus(type, Money.ofMinor(minor, currency), on, note), "GBP", HIRED, TODAY);
    }

    @Test
    void acceptsAValidAward() {
        assertThatCode(() -> check(BonusType.PERFORMANCE, 500_000, "GBP", TODAY, null)).doesNotThrowAnyException();
        assertThatCode(() -> check(BonusType.SPOT, 100_000, "GBP", TODAY, "Saved the launch")).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhatHrWouldConsiderAMistake() {
        assertThatThrownBy(() -> check(BonusType.PERFORMANCE, 0, "GBP", TODAY, null)).hasMessageContaining("positive");
        assertThatThrownBy(() -> check(BonusType.PERFORMANCE, 100, "USD", TODAY, null)).hasMessageContaining("GBP");
        assertThatThrownBy(() -> check(BonusType.SIGN_ON, 100, "GBP", HIRED.minusDays(1), null)).hasMessageContaining("hire date");
        assertThatThrownBy(() -> check(BonusType.RETENTION, 100, "GBP", TODAY.plusYears(1).plusDays(1), null)).hasMessageContaining("year ahead");
    }

    @Test
    void spotBonusesMustSayWhatTheyRecognise() {
        assertThatThrownBy(() -> check(BonusType.SPOT, 100, "GBP", TODAY, "  ")).hasMessageContaining("note");
    }
}
