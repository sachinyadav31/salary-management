package com.acme.salary.employee.query;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;

/**
 * A single employee: the directory row plus what an edit form needs (version for optimistic locking) and who
 * last edited it (null if never edited by a person).
 */
public record EmployeeDetail(
        @JsonUnwrapped EmployeeSummary summary,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) {
}
