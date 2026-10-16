package com.acme.salary.common.web;

import com.acme.salary.common.Money;

/**
 * JSON shape for money: exact minor units for computation, plus a decimal string for display.
 * The amount is a string so JavaScript never sees it as a lossy float.
 */
public record MoneyView(long minorUnits, String currency, String amount) {

    public static MoneyView from(Money money) {
        return new MoneyView(money.minorUnits(), money.currencyCode(), money.toDecimal().toPlainString());
    }
}
