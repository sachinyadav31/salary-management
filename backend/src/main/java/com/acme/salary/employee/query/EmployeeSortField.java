package com.acme.salary.employee.query;

import java.util.Arrays;
import java.util.Locale;

/**
 * Whitelisted sort fields. The API accepts {@code field[,asc|desc]}; only these columns can ever
 * reach the ORDER BY clause, so sorting cannot be used for SQL injection.
 */
public enum EmployeeSortField {
    NAME("name", "e.last_name %1$s, e.first_name %1$s"),
    EMPLOYEE_CODE("employeeCode", "e.employee_code %s"),
    COUNTRY("country", "c.name %s"),
    DEPARTMENT("department", "d.name %s"),
    JOB_TITLE("jobTitle", "e.job_title %s"),
    LEVEL("level", "e.level %s"),
    HIRE_DATE("hireDate", "e.hire_date %s"),
    SALARY_USD("salaryUsd", "e.current_salary_usd_minor %s"),
    COMPA_RATIO("compaRatio", "p.compa_ratio_bp %s");

    public record Sort(EmployeeSortField field, boolean descending) {
        public static final Sort DEFAULT = new Sort(NAME, false);

        String orderBy() {
            return field.template.formatted(descending ? "desc" : "asc");
        }
    }

    private final String apiName;
    private final String template;

    EmployeeSortField(String apiName, String template) {
        this.apiName = apiName;
        this.template = template;
    }

    public static Sort parse(String value) {
        if (value == null || value.isBlank()) {
            return Sort.DEFAULT;
        }
        String[] parts = value.split(",", 2);
        EmployeeSortField field = Arrays.stream(values())
                .filter(f -> f.apiName.equalsIgnoreCase(parts[0].trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort field: " + parts[0]));
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalArgumentException("Sort direction must be asc or desc");
        }
        return new Sort(field, direction.equals("desc"));
    }
}
