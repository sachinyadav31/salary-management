package com.acme.salary.meta.api;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.reference.JobLevel;

import java.util.List;

/** Everything the UI needs to populate filters and forms, in one request. */
public record MetaResponse(
        List<CountryView> countries,
        List<DepartmentView> departments,
        List<String> jobTitles,
        List<JobLevel> levels,
        List<EmployeeStatus> statuses,
        String reportingCurrency,
        String fxRatesAsOf) {

    public record CountryView(String code, String name, String currency) {
    }

    public record DepartmentView(long id, String name) {
    }
}
