package com.acme.salary.reference;

import com.acme.salary.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * The single place where local-currency amounts are converted to the reporting currency (USD).
 * Rates come from a fixed, dated FX table so reports are reproducible.
 */
public class CurrencyConverter {

    public static final String REPORTING_CURRENCY = "USD";

    private final Map<String, BigDecimal> usdPerUnit;

    public CurrencyConverter(Map<String, BigDecimal> usdPerUnit) {
        this.usdPerUnit = Map.copyOf(usdPerUnit);
    }

    public Money toUsd(Money money) {
        if (money.currencyCode().equals(REPORTING_CURRENCY)) {
            return money;
        }
        BigDecimal usdAmount = money.toDecimal().multiply(rateFor(money.currencyCode()));
        return Money.of(usdAmount.setScale(2, RoundingMode.HALF_EVEN), REPORTING_CURRENCY);
    }

    public BigDecimal rateFor(String currencyCode) {
        if (currencyCode.equals(REPORTING_CURRENCY)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = usdPerUnit.get(currencyCode);
        if (rate == null) {
            throw new IllegalStateException("No FX rate configured for " + currencyCode);
        }
        return rate;
    }
}
