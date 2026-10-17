package com.acme.salary.insights;

import com.acme.salary.employee.JobLevel;
import com.acme.salary.insights.InsightsModels.CountryInsight;
import com.acme.salary.payband.BandPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A tiny workforce whose answers can be checked by hand. */
class InsightsCalculatorTest {

    private static final Instant AS_OF = Instant.parse("2026-10-17T08:00:00Z");

    //                                   country   dept  title                level   local (minor)   usd (cents)   compa  position
    private static final List<WorkforceMember> PEOPLE = List.of(
            member("US", 1, "Software Engineer", JobLevel.L2, 9_000_000, 9_000_000, 9_900, BandPosition.WITHIN),
            member("US", 1, "Software Engineer", JobLevel.L2, 11_000_000, 11_000_000, 12_100, BandPosition.ABOVE),
            member("US", 4, "Account Executive", JobLevel.L2, 7_000_000, 7_000_000, 7_700, BandPosition.BELOW),
            member("JP", 1, "Software Engineer", JobLevel.L2, 6_000_000, 4_020_000, 10_300, BandPosition.WITHIN),
            member("JP", 1, "Software Engineer", JobLevel.L2, 7_000_000, 4_690_000, 12_000, BandPosition.WITHIN));

    private final WorkforceSnapshot snapshot = new WorkforceSnapshot(PEOPLE, AS_OF, "2026-10-01");

    @Test
    void summaryAddsUpPayrollInUsd() {
        var summary = InsightsCalculator.summary(snapshot);

        assertThat(summary.headcount()).isEqualTo(5);
        assertThat(summary.countries()).isEqualTo(2);
        assertThat(summary.totalPayroll().amount()).isEqualTo("357100.00");   // 90k+110k+70k+40.2k+46.9k
        assertThat(summary.averageSalary().amount()).isEqualTo("71420.00");
        assertThat(summary.medianSalary().amount()).isEqualTo("70000.00");
        assertThat(summary.bands().below()).isEqualTo(1);
        assertThat(summary.bands().within()).isEqualTo(3);
        assertThat(summary.bands().above()).isEqualTo(1);
        assertThat(summary.bands().withinPercent()).isEqualByComparingTo("60.0");
        assertThat(summary.bands().averageCompaRatio()).isEqualByComparingTo("1.04"); // 52,000 / 5 = 10,400 bp
        assertThat(summary.asOf()).isEqualTo(AS_OF);
    }

    @Test
    void byCountryReportsLocalAndUsdStatisticsLargestPayrollFirst() {
        List<CountryInsight> countries = InsightsCalculator.byCountry(snapshot);

        assertThat(countries).extracting(CountryInsight::countryCode).containsExactly("US", "JP");
        CountryInsight japan = countries.get(1);
        assertThat(japan.currency()).isEqualTo("JPY");
        assertThat(japan.payroll().amount()).isEqualTo("13000000");
        assertThat(japan.local().median().amount()).isEqualTo("6500000");
        assertThat(japan.usd().average().amount()).isEqualTo("43550.00");
        assertThat(japan.bands().withinPercent()).isEqualByComparingTo("100.0");
    }

    @Test
    void byTitleComparesPeersWithinOneCountry() {
        var titles = InsightsCalculator.byTitle(snapshot, "US");

        assertThat(titles).hasSize(2);
        var engineers = titles.stream().filter(t -> t.jobTitle().equals("Software Engineer")).findFirst().orElseThrow();
        assertThat(engineers.headcount()).isEqualTo(2);
        assertThat(engineers.stats().min().amount()).isEqualTo("90000.00");
        assertThat(engineers.stats().max().amount()).isEqualTo("110000.00");
        assertThat(engineers.spread()).isEqualByComparingTo("1.22");
        assertThat(engineers.department()).isEqualTo("Dept 1");
    }

    @Test
    void distributionUsesLocalCurrencyForOneCountryAndUsdOtherwise() {
        var japan = InsightsCalculator.distribution(snapshot, "JP", null);
        var everyone = InsightsCalculator.distribution(snapshot, null, null);
        var engineering = InsightsCalculator.distribution(snapshot, null, 1L);

        assertThat(japan.currency()).isEqualTo("JPY");
        assertThat(japan.headcount()).isEqualTo(2);
        assertThat(everyone.currency()).isEqualTo("USD");
        assertThat(everyone.buckets().stream().mapToLong(InsightsModels.DistributionBucket::count).sum()).isEqualTo(5);
        assertThat(engineering.headcount()).isEqualTo(4);
    }

    @Test
    void emptySelectionsAreHandledGracefully() {
        assertThat(InsightsCalculator.byTitle(snapshot, "DE")).isEmpty();
        var none = InsightsCalculator.distribution(snapshot, "DE", null);
        assertThat(none.buckets()).isEmpty();
        assertThat(none.stats()).isNull();
        var emptySummary = InsightsCalculator.summary(new WorkforceSnapshot(List.of(), AS_OF, "2026-10-01"));
        assertThat(emptySummary.headcount()).isZero();
        assertThat(emptySummary.medianSalary()).isNull();
    }

    private static WorkforceMember member(String country, long dept, String title, JobLevel level, long local,
                                          long usd, long compaBp, BandPosition position) {
        return new WorkforceMember(country, country.equals("US") ? "United States" : "Japan",
                country.equals("US") ? "USD" : "JPY", dept, "Dept " + dept, title, level, local, usd, compaBp, position);
    }
}
