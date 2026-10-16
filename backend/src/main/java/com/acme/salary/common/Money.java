package com.acme.salary.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a specific currency, held as integer minor units (e.g. cents).
 * Never uses floating point. The number of minor units per major unit follows ISO 4217
 * (USD = 2 decimals, JPY = 0).
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money ofMinor(long minorUnits, String currencyCode) {
        return new Money(minorUnits, currency(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        Currency currency = currency(currencyCode);
        int scale = currency.getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > scale) {
            throw new IllegalArgumentException(
                    "%s allows at most %d decimal places, got %s".formatted(currencyCode, scale, amount));
        }
        return new Money(amount.movePointRight(scale).longValueExact(), currency);
    }

    public static Currency currency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown currency: " + currencyCode, e);
        }
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits());
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add %s to %s".formatted(other.currencyCode(), currencyCode()));
        }
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currencyCode();
    }
}
