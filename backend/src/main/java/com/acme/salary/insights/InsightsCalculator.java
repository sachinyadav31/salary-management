package com.acme.salary.insights;

import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.insights.InsightsModels.BandSplit;
import com.acme.salary.insights.InsightsModels.CountryInsight;
import com.acme.salary.insights.InsightsModels.Distribution;
import com.acme.salary.insights.InsightsModels.DistributionBucket;
import com.acme.salary.insights.InsightsModels.Stats;
import com.acme.salary.insights.InsightsModels.Summary;
import com.acme.salary.insights.InsightsModels.TitleInsight;
import com.acme.salary.payband.BandPosition;
import com.acme.salary.payband.CompaRatio;
import com.acme.salary.reference.CurrencyConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/** Turns a workforce snapshot into dashboard insights. Pure functions, no I/O. */
public final class InsightsCalculator {

    static final int DISTRIBUTION_BUCKETS = 12;
    private static final String USD = CurrencyConverter.REPORTING_CURRENCY;

    private InsightsCalculator() {
    }

    public static Summary summary(WorkforceSnapshot snapshot) {
        List<WorkforceMember> members = snapshot.members();
        if (members.isEmpty()) {
            return new Summary(snapshot.asOf(), snapshot.fxRatesAsOf(), USD, 0, 0, money(0, USD), null, null,
                    bandSplit(members), money(0, USD), money(0, USD), money(0, USD));
        }
        PayStatistics usd = stats(members, WorkforceMember::salaryUsdMinor);
        long variable = sum(members, WorkforceMember::variableTargetUsdMinor);
        long bonuses = sum(members, WorkforceMember::bonusesUsdMinorLast12Months);
        return new Summary(snapshot.asOf(), snapshot.fxRatesAsOf(), USD, members.size(),
                (int) members.stream().map(WorkforceMember::countryCode).distinct().count(),
                money(usd.sum(), USD), money(usd.average(), USD), money(usd.median(), USD), bandSplit(members),
                money(variable, USD), money(Math.addExact(usd.sum(), variable), USD), money(bonuses, USD));
    }

    /** Largest payroll (USD) first. */
    public static List<CountryInsight> byCountry(WorkforceSnapshot snapshot) {
        return groupBy(snapshot.members(), WorkforceMember::countryCode).values().stream()
                .map(group -> {
                    WorkforceMember first = group.getFirst();
                    PayStatistics local = stats(group, WorkforceMember::salaryMinor);
                    PayStatistics usd = stats(group, WorkforceMember::salaryUsdMinor);
                    return new CountryInsight(first.countryCode(), first.countryName(), first.currency(), group.size(),
                            money(local.sum(), first.currency()), money(usd.sum(), USD),
                            statsView(local, first.currency()), statsView(usd, USD), bandSplit(group),
                            money(sum(group, WorkforceMember::variableTargetUsdMinor), USD),
                            money(sum(group, WorkforceMember::bonusesUsdMinorLast12Months), USD));
                })
                .sorted(Comparator.comparing((CountryInsight c) -> c.payrollUsd().minorUnits()).reversed())
                .toList();
    }

    /** Job titles within one country, in local currency; ordered by department, then level. */
    public static List<TitleInsight> byTitle(WorkforceSnapshot snapshot, String countryCode) {
        List<WorkforceMember> inCountry = snapshot.members().stream()
                .filter(m -> m.countryCode().equals(countryCode)).toList();
        return groupBy(inCountry, WorkforceMember::jobTitle).values().stream()
                .map(group -> {
                    WorkforceMember first = group.getFirst();
                    PayStatistics local = stats(group, WorkforceMember::salaryMinor);
                    return new TitleInsight(first.jobTitle(), first.level(), first.departmentName(), group.size(),
                            statsView(local, first.currency()), ratio(local.max(), local.min()), bandSplit(group));
                })
                .sorted(Comparator.comparing(TitleInsight::department).thenComparing(TitleInsight::level))
                .toList();
    }

    /** Salary histogram. Local currency when a country is given (salaries are comparable); otherwise USD. */
    public static Distribution distribution(WorkforceSnapshot snapshot, String countryCode, Long departmentId) {
        List<WorkforceMember> selected = snapshot.members().stream()
                .filter(m -> countryCode == null || m.countryCode().equals(countryCode))
                .filter(m -> departmentId == null || m.departmentId() == departmentId)
                .toList();
        boolean local = countryCode != null;
        String currency = local && !selected.isEmpty() ? selected.getFirst().currency() : USD;
        ToLongFunction<WorkforceMember> salary = local ? WorkforceMember::salaryMinor : WorkforceMember::salaryUsdMinor;

        List<Long> values = selected.stream().map(salary::applyAsLong).toList();
        List<DistributionBucket> buckets = Histogram.of(values, DISTRIBUTION_BUCKETS).stream()
                .map(b -> new DistributionBucket(money(b.fromMinor(), currency), money(b.toMinor(), currency), b.count()))
                .toList();
        return new Distribution(snapshot.asOf(), currency, selected.size(), buckets,
                values.isEmpty() ? null : statsView(PayStatistics.of(values), currency));
    }

    static BandSplit bandSplit(List<WorkforceMember> members) {
        Map<BandPosition, Long> counts = members.stream().filter(m -> m.bandPosition() != null)
                .collect(Collectors.groupingBy(WorkforceMember::bandPosition, Collectors.counting()));
        long below = counts.getOrDefault(BandPosition.BELOW, 0L);
        long within = counts.getOrDefault(BandPosition.WITHIN, 0L);
        long above = counts.getOrDefault(BandPosition.ABOVE, 0L);
        long banded = below + within + above;
        List<Long> compa = members.stream().map(WorkforceMember::compaRatioBp).filter(Objects::nonNull).toList();
        return new BandSplit(below, within, above,
                banded == 0 ? null : BigDecimal.valueOf(within * 100).divide(BigDecimal.valueOf(banded), 1, RoundingMode.HALF_UP),
                compa.isEmpty() ? null : CompaRatio.fromBasisPoints(PayStatistics.of(compa).average()));
    }

    private static <K> Map<K, List<WorkforceMember>> groupBy(List<WorkforceMember> members,
                                                            java.util.function.Function<WorkforceMember, K> key) {
        return members.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static long sum(List<WorkforceMember> members, ToLongFunction<WorkforceMember> value) {
        return members.stream().mapToLong(value).reduce(0, Math::addExact);
    }

    private static PayStatistics stats(List<WorkforceMember> members, ToLongFunction<WorkforceMember> value) {
        return PayStatistics.of(members.stream().map(value::applyAsLong).toList());
    }

    private static Stats statsView(PayStatistics s, String currency) {
        return new Stats(money(s.min(), currency), money(s.max(), currency), money(s.average(), currency),
                money(s.median(), currency));
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static MoneyView money(long minor, String currency) {
        return MoneyView.from(Money.ofMinor(minor, currency));
    }
}
