package com.acme.salary.payband;

import com.acme.salary.reference.JobLevel;

/** A band with how many active employees sit below, within and above it. */
public record PayBandSummary(String countryCode, String countryName, JobLevel level, PayBandView band,
                             long headcount, long below, long within, long above) {
}
