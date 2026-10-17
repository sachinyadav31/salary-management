package com.acme.salary.seed;

import com.acme.salary.common.Money;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;

import java.time.LocalDate;
import java.util.List;

/** A generated employee with its salary history (oldest first). */
public record SeedEmployee(
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String countryCode,
        String departmentName,
        String jobTitle,
        JobLevel level,
        LocalDate hireDate,
        EmployeeStatus status,
        Money currentSalary,
        List<SeedSalaryRecord> salaryHistory,
        List<SeedBonus> bonuses) {
}
