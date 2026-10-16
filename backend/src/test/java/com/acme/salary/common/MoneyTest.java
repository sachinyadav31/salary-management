package com.acme.salary.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void createsFromDecimalAmountUsingCurrencyMinorUnits() {
        assertThat(Money.of(new BigDecimal("1234.56"), "USD").minorUnits()).isEqualTo(123456);
        assertThat(Money.of(new BigDecimal("150000"), "JPY").minorUnits()).isEqualTo(150000);
    }

    @Test
    void convertsBackToDecimalWithCurrencyScale() {
        assertThat(Money.ofMinor(123456, "USD").toDecimal()).isEqualByComparingTo("1234.56");
        assertThat(Money.ofMinor(150000, "JPY").toDecimal()).isEqualByComparingTo("150000");
    }

    @Test
    void rejectsMoreDecimalPlacesThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.001"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.5"), "JPY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> Money.ofMinor(100, "XXZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsMoneyOfSameCurrency() {
        assertThat(Money.ofMinor(100, "EUR").plus(Money.ofMinor(250, "EUR")))
                .isEqualTo(Money.ofMinor(350, "EUR"));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        assertThatThrownBy(() -> Money.ofMinor(100, "EUR").plus(Money.ofMinor(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knowsWhetherItIsPositive() {
        assertThat(Money.ofMinor(1, "USD").isPositive()).isTrue();
        assertThat(Money.ofMinor(0, "USD").isPositive()).isFalse();
        assertThat(Money.ofMinor(-5, "USD").isPositive()).isFalse();
    }
}
