package com.acme.salary.employee.api;

import com.acme.salary.common.csv.CsvWriter;
import com.acme.salary.employee.query.EmployeeSummary;

/** Column layout of the employee CSV export. Amounts are plain decimals with a separate currency column. */
final class EmployeeCsvFormat {

    static final String[] HEADER = {
            "Employee code", "First name", "Last name", "Email", "Country", "Department", "Job title", "Level",
            "Status", "Hire date", "Salary", "Currency", "Salary (USD)", "Band min", "Band midpoint", "Band max",
            "Compa-ratio", "Band position"};

    private EmployeeCsvFormat() {
    }

    static void writeHeader(CsvWriter csv) {
        for (String column : HEADER) {
            csv.text(column);
        }
        csv.endRow();
    }

    static void writeRow(CsvWriter csv, EmployeeSummary e) {
        csv.text(e.employeeCode())
                .text(e.firstName())
                .text(e.lastName())
                .text(e.email())
                .text(e.countryName())
                .text(e.department())
                .text(e.jobTitle())
                .text(e.level().name())
                .text(e.status().name())
                .text(e.hireDate().toString())
                .number(e.salary().amount())
                .text(e.salary().currency())
                .number(e.salaryUsd().amount())
                .number(e.payBand() == null ? null : e.payBand().min().amount())
                .number(e.payBand() == null ? null : e.payBand().mid().amount())
                .number(e.payBand() == null ? null : e.payBand().max().amount())
                .number(e.compaRatio() == null ? null : e.compaRatio().toPlainString())
                .text(e.bandPosition() == null ? null : e.bandPosition().name())
                .endRow();
    }
}
