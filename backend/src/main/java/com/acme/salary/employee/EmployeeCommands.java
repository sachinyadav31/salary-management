package com.acme.salary.employee;

import com.acme.salary.reference.JobLevel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inputs to {@link EmployeeService}. Kept separate from HTTP request bodies so the domain has no web dependency. */
public final class EmployeeCommands {

    private EmployeeCommands() {
    }

    /** Salary currency is not an input: it is always the country's currency. */
    public record CreateEmployee(String firstName, String lastName, String email, String countryCode,
                                 long departmentId, String jobTitle, JobLevel level, LocalDate hireDate,
                                 BigDecimal salaryAmount) {
    }

    /**
     * {@code expectedVersion} is the version the client loaded; a mismatch means someone else saved first.
     * {@code variableTargetPercent} null = unchanged.
     */
    public record UpdateProfile(String firstName, String lastName, String email, long departmentId,
                                String jobTitle, JobLevel level, BigDecimal variableTargetPercent, long expectedVersion) {

        public UpdateProfile(String firstName, String lastName, String email, long departmentId, String jobTitle,
                             JobLevel level, long expectedVersion) {
            this(firstName, lastName, email, departmentId, jobTitle, level, null, expectedVersion);
        }
    }
}
