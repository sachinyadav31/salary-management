package com.acme.salary.employee.query;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.payband.BandPosition;

import java.math.BigDecimal;

import java.util.List;

/** All optional filters for the employee directory. Empty lists / nulls mean "no filter". */
public record EmployeeSearchCriteria(
        String search,
        List<String> countries,
        List<Long> departmentIds,
        String jobTitle,
        List<JobLevel> levels,
        EmployeeStatus status,
        BandPosition bandPosition,
        BigDecimal compaMin,
        BigDecimal compaMax,
        int page,
        int size,
        EmployeeSortField.Sort sort) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public EmployeeSearchCriteria {
        countries = countries == null ? List.of() : List.copyOf(countries);
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        levels = levels == null ? List.of() : List.copyOf(levels);
        sort = sort == null ? EmployeeSortField.Sort.DEFAULT : sort;
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (compaMin != null && compaMax != null && compaMin.compareTo(compaMax) > 0) {
            throw new IllegalArgumentException("compaMin must not be greater than compaMax");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String search;
        private List<String> countries;
        private List<Long> departmentIds;
        private String jobTitle;
        private List<JobLevel> levels;
        private EmployeeStatus status;
        private BandPosition bandPosition;
        private BigDecimal compaMin;
        private BigDecimal compaMax;
        private int page;
        private int size = DEFAULT_SIZE;
        private EmployeeSortField.Sort sort;

        public Builder search(String search) { this.search = search; return this; }
        public Builder countries(List<String> countries) { this.countries = countries; return this; }
        public Builder departmentIds(List<Long> departmentIds) { this.departmentIds = departmentIds; return this; }
        public Builder jobTitle(String jobTitle) { this.jobTitle = jobTitle; return this; }
        public Builder levels(List<JobLevel> levels) { this.levels = levels; return this; }
        public Builder status(EmployeeStatus status) { this.status = status; return this; }
        public Builder bandPosition(BandPosition bandPosition) { this.bandPosition = bandPosition; return this; }
        public Builder compaMin(BigDecimal compaMin) { this.compaMin = compaMin; return this; }
        public Builder compaMax(BigDecimal compaMax) { this.compaMax = compaMax; return this; }
        public Builder page(int page) { this.page = page; return this; }
        public Builder size(int size) { this.size = size; return this; }
        public Builder sort(EmployeeSortField.Sort sort) { this.sort = sort; return this; }
        public Builder sort(EmployeeSortField field, boolean descending) {
            return sort(new EmployeeSortField.Sort(field, descending));
        }

        public EmployeeSearchCriteria build() {
            return new EmployeeSearchCriteria(search, countries, departmentIds, jobTitle, levels, status, bandPosition,
                    compaMin, compaMax, page, size, sort);
        }
    }
}
