package com.acme.salary.employee.query;

import com.acme.salary.common.web.MoneyView;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;

import java.time.LocalDate;

/** One row of the employee directory (read model). */
public record EmployeeSummary(
        long id,
        String employeeCode,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String countryCode,
        String countryName,
        long departmentId,
        String department,
        String jobTitle,
        JobLevel level,
        EmployeeStatus status,
        LocalDate hireDate,
        MoneyView salary,
        MoneyView salaryUsd) {
}
