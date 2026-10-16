package com.acme.salary.employee.api;

import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.employee.query.EmployeeQueryRepository;
import com.acme.salary.employee.query.EmployeeSearchCriteria;
import com.acme.salary.employee.query.EmployeeSortField;
import com.acme.salary.employee.query.EmployeeSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeQueryRepository queries;

    public EmployeeController(EmployeeQueryRepository queries) {
        this.queries = queries;
    }

    /**
     * Paged directory. Repeat a parameter to filter by several values, e.g. {@code ?country=IN&country=JP}.
     * Sort is {@code field[,asc|desc]} with field one of name, employeeCode, country, department, jobTitle,
     * level, hireDate, salaryUsd.
     */
    @GetMapping
    public PageResponse<EmployeeSummary> search(
            @RequestParam(required = false) String search,
            @RequestParam(name = "country", required = false) List<String> countries,
            @RequestParam(name = "department", required = false) List<Long> departmentIds,
            @RequestParam(name = "title", required = false) String jobTitle,
            @RequestParam(name = "level", required = false) List<JobLevel> levels,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + EmployeeSearchCriteria.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort) {
        return queries.search(EmployeeSearchCriteria.builder()
                .search(search)
                .countries(countries)
                .departmentIds(departmentIds)
                .jobTitle(jobTitle)
                .levels(levels)
                .status(status)
                .page(page)
                .size(size)
                .sort(EmployeeSortField.parse(sort))
                .build());
    }
}
