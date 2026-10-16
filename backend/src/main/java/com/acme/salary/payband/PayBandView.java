package com.acme.salary.payband;

import com.acme.salary.common.web.MoneyView;

/** A pay band's range in local currency. */
public record PayBandView(MoneyView min, MoneyView mid, MoneyView max) {
}
