package com.acme.salary.insights;

import com.acme.salary.payband.BandPosition;
import com.acme.salary.reference.JobLevel;

/** The fields insights need about one active employee. Compa/position are null if no band exists. */
public record WorkforceMember(
        String countryCode,
        String countryName,
        String currency,
        long departmentId,
        String departmentName,
        String jobTitle,
        JobLevel level,
        long salaryMinor,
        long salaryUsdMinor,
        Long compaRatioBp,
        BandPosition bandPosition) {
}
