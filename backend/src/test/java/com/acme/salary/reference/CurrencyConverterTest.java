package com.acme.salary.reference;

import com.acme.salary.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter(Map.of(
            "INR", new BigDecimal("0.012"),
            "JPY", new BigDecimal("0.0067"),
            "GBP", new BigDecimal("1.27")));

    @Test
    void usdIsReturnedUnchanged() {
        assertThat(converter.toUsd(Money.ofMinor(123456, "USD"))).isEqualTo(Money.ofMinor(123456, "USD"));
    }

    @Test
    void convertsUsingUsdPerUnitRate() {
        // 1,00,000.00 INR * 0.012 = 1,200.00 USD
        assertThat(converter.toUsd(Money.ofMinor(10_000_000, "INR"))).isEqualTo(Money.ofMinor(120_000, "USD"));
    }

    @Test
    void handlesZeroDecimalCurrencies() {
        // 1,000,000 JPY * 0.0067 = 6,700.00 USD
        assertThat(converter.toUsd(Money.ofMinor(1_000_000, "JPY"))).isEqualTo(Money.ofMinor(670_000, "USD"));
    }

    @Test
    void roundsHalfEvenToCents() {
        // 0.01 GBP * 1.27 = 0.0127 USD -> 0.01
        assertThat(converter.toUsd(Money.ofMinor(1, "GBP"))).isEqualTo(Money.ofMinor(1, "USD"));
        // 0.05 GBP * 1.27 = 0.0635 -> 0.06
        assertThat(converter.toUsd(Money.ofMinor(5, "GBP"))).isEqualTo(Money.ofMinor(6, "USD"));
    }

    @Test
    void failsLoudlyWhenRateIsMissing() {
        assertThatThrownBy(() -> converter.toUsd(Money.ofMinor(100, "EUR")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EUR");
    }
}
