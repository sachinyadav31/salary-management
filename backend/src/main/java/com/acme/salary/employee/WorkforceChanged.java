package com.acme.salary.employee;

/**
 * Published by every write that can change insights (hire, profile, status, salary, rollover, seed).
 * Read models such as the insights cache listen for it after the transaction commits.
 */
public record WorkforceChanged(String reason) {
}
