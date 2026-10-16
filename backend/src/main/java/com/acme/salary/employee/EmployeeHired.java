package com.acme.salary.employee;

import com.acme.salary.common.Money;

import java.time.Instant;

/**
 * Published when an employee is created. Lets the salary feature record the HIRE entry without the
 * employee feature depending on it (no package cycle). Listeners run synchronously in the same transaction.
 */
public record EmployeeHired(Employee employee, Money startingSalary, Instant at) {
}
