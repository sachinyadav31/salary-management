package com.acme.salary.employee.api;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSortField;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.reference.JobLevel;

import java.math.BigDecimal;
import java.util.List;

/**
 * Directory filters bound from the query string (component names are the parameter names). Shared by
 * search and CSV export, so both accept exactly the same filters. Repeat a parameter for several values,
 * e.g. {@code ?country=IN&country=JP}.
 */
public record EmployeeFilterParams(
        String search,
        List<String> country,
        List<Long> department,
        String title,
        List<JobLevel> level,
        EmployeeStatus status,
        BandPosition bandPosition,
        BigDecimal compaMin,
        BigDecimal compaMax,
        String sort) {

    EmployeeSearchCriteria.Builder toCriteria() {
        return EmployeeSearchCriteria.builder()
                .search(search)
                .countries(country)
                .departmentIds(department)
                .jobTitle(title)
                .levels(level)
                .status(status)
                .bandPosition(bandPosition)
                .compaMin(compaMin)
                .compaMax(compaMax)
                .sort(EmployeeSortField.parse(sort));
    }
}
