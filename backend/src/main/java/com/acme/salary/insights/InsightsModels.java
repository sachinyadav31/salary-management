package com.acme.salary.insights;

import com.acme.salary.common.web.MoneyView;
import com.acme.salary.reference.JobLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Response shapes for the insights API. Money is always exact (minor units + decimal string). */
public final class InsightsModels {

    private InsightsModels() {
    }

    public record Stats(MoneyView min, MoneyView max, MoneyView average, MoneyView median) {
    }

    public record BandSplit(long below, long within, long above, BigDecimal withinPercent,
                            BigDecimal averageCompaRatio) {
    }

    public record Summary(Instant asOf, String fxRatesAsOf, String reportingCurrency, long headcount,
                          int countries, MoneyView totalPayroll, MoneyView averageSalary, MoneyView medianSalary,
                          BandSplit bands) {
    }

    public record CountryInsight(String countryCode, String countryName, String currency, long headcount,
                                 MoneyView payroll, MoneyView payrollUsd, Stats local, Stats usd, BandSplit bands) {
    }

    /** {@code spread} = highest / lowest salary for the title, e.g. 1.80 means the top earner gets 80% more. */
    public record TitleInsight(String jobTitle, JobLevel level, String department, long headcount, Stats stats,
                               BigDecimal spread, BandSplit bands) {
    }

    public record DistributionBucket(MoneyView from, MoneyView to, long count) {
    }

    public record Distribution(Instant asOf, String currency, long headcount, List<DistributionBucket> buckets,
                               Stats stats) {
    }

    public record Envelope<T>(Instant asOf, String fxRatesAsOf, T data) {
    }
}
