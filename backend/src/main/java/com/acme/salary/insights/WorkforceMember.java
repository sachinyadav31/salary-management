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
        BandPosition bandPosition,
        long variableTargetUsdMinor,
        long bonusesUsdMinorLast12Months) {

    /** Without compensation figures (zero variable target and bonuses), for callers that only need base pay. */
    public WorkforceMember(String countryCode, String countryName, String currency, long departmentId, String departmentName,
                           String jobTitle, JobLevel level, long salaryMinor, long salaryUsdMinor, Long compaRatioBp,
                           BandPosition bandPosition) {
        this(countryCode, countryName, currency, departmentId, departmentName, jobTitle, level, salaryMinor, salaryUsdMinor,
                compaRatioBp, bandPosition, 0, 0);
    }
}
