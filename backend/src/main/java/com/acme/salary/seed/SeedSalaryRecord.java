package com.acme.salary.seed;

import com.acme.salary.common.Money;
import com.acme.salary.salary.SalaryChangeReason;

import java.time.LocalDate;

public record SeedSalaryRecord(Money amount, LocalDate effectiveDate, SalaryChangeReason reason) {
}
