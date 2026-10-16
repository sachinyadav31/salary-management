package com.acme.salary.employee.query;

import com.acme.salary.common.web.MoneyView;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.payband.PayBandView;

import java.math.BigDecimal;

import java.time.LocalDate;

/**
 * One row of the employee directory (read model). Pay band fields are null if no band is defined
 * for the employee's country and level.
 */
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
        MoneyView salaryUsd,
        PayBandView payBand,
        BigDecimal compaRatio,
        BandPosition bandPosition) {
}
