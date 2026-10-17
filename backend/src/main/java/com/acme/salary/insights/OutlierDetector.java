package com.acme.salary.insights;

import com.acme.salary.payband.BandPosition;
import com.acme.salary.payband.CompaRatio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds pay patterns worth an HR manager's attention and ranks them. Pure rules over a snapshot.
 * <p>
 * Every rule measures distance from fair pay in the same unit, compa-ratio points (1 point = 0.01), so
 * severity = people affected × points of deviation is comparable across rule types.
 */
public class OutlierDetector {

    /** Smaller groups are noisy and could identify individuals. */
    static final int MIN_GROUP_SIZE = 5;
    /** A title is an underpaid cluster when at least this share of its people are below band. */
    static final int UNDERPAID_SHARE_PERCENT = 15;
    /** Higher than the underpaid threshold: overpaying is a cost issue, underpaying a fairness/retention risk. */
    static final int OVERPAID_SHARE_PERCENT = 20;
    /** Peers in one band spanning at least 0.50 compa-ratio suggests inconsistent pay decisions. */
    static final long WIDE_SPREAD_MIN_BP = 5_000;
    /** A department averaging below 0.90 of midpoint across the org is a systemic gap. */
    static final long DEPARTMENT_LAG_MAX_BP = 9_000;

    public enum OutlierType { UNDERPAID_GROUP, OVERPAID_GROUP, WIDE_SPREAD, DEPARTMENT_LAG }

    /** {@code filter} holds directory query parameters that list exactly the people behind the card. */
    public record OutlierCard(String id, OutlierType type, long severity, String headline, String detail,
                              long affected, long groupSize, Map<String, String> filter) {
    }

    private record TitleGroup(String countryCode, String jobTitle) implements Comparable<TitleGroup> {
        private static final Comparator<TitleGroup> ORDER =
                Comparator.comparing(TitleGroup::countryCode).thenComparing(TitleGroup::jobTitle);

        @Override
        public int compareTo(TitleGroup other) {
            return ORDER.compare(this, other);
        }
    }

    public List<OutlierCard> detect(WorkforceSnapshot snapshot, int limit) {
        List<WorkforceMember> banded = snapshot.members().stream()
                .filter(m -> m.compaRatioBp() != null).toList();
        List<OutlierCard> cards = new ArrayList<>();

        groupBy(banded, m -> new TitleGroup(m.countryCode(), m.jobTitle())).values().stream()
                .filter(group -> group.size() >= MIN_GROUP_SIZE)
                .forEach(group -> {
                    underpaid(group).ifPresent(cards::add);
                    overpaid(group).ifPresent(cards::add);
                    wideSpread(group).ifPresent(cards::add);
                });
        groupBy(banded, WorkforceMember::departmentId).values().stream()
                .filter(group -> group.size() >= MIN_GROUP_SIZE)
                .map(OutlierDetector::departmentLag)
                .flatMap(Optional::stream)
                .forEach(cards::add);

        return cards.stream()
                .sorted(Comparator.comparingLong(OutlierCard::severity).reversed().thenComparing(OutlierCard::id))
                .limit(limit)
                .toList();
    }

    private static Optional<OutlierCard> underpaid(List<WorkforceMember> group) {
        List<WorkforceMember> below = withPosition(group, BandPosition.BELOW);
        if (below.size() * 100L < UNDERPAID_SHARE_PERCENT * (long) group.size()) {
            return Optional.empty();
        }
        WorkforceMember first = group.getFirst();
        long averageBp = averageCompa(below);
        return Optional.of(new OutlierCard(
                id(OutlierType.UNDERPAID_GROUP, first), OutlierType.UNDERPAID_GROUP,
                severity(below.size(), 10_000 - averageBp),
                "In %s, %d of %d people in %s roles are paid below their pay band"
                        .formatted(first.countryName(), below.size(), group.size(), first.jobTitle()),
                "Average compa-ratio of those below band: " + CompaRatio.fromBasisPoints(averageBp),
                below.size(), group.size(),
                filter("country", first.countryCode(), "title", first.jobTitle(),
                        "bandPosition", "BELOW", "sort", "compaRatio,asc")));
    }

    private static Optional<OutlierCard> overpaid(List<WorkforceMember> group) {
        List<WorkforceMember> above = withPosition(group, BandPosition.ABOVE);
        if (above.size() * 100L < OVERPAID_SHARE_PERCENT * (long) group.size()) {
            return Optional.empty();
        }
        WorkforceMember first = group.getFirst();
        long averageBp = averageCompa(above);
        return Optional.of(new OutlierCard(
                id(OutlierType.OVERPAID_GROUP, first), OutlierType.OVERPAID_GROUP,
                severity(above.size(), averageBp - 10_000),
                "In %s, %d of %d people in %s roles are paid above their pay band"
                        .formatted(first.countryName(), above.size(), group.size(), first.jobTitle()),
                "Average compa-ratio of those above band: " + CompaRatio.fromBasisPoints(averageBp),
                above.size(), group.size(),
                filter("country", first.countryCode(), "title", first.jobTitle(),
                        "bandPosition", "ABOVE", "sort", "compaRatio,desc")));
    }

    private static Optional<OutlierCard> wideSpread(List<WorkforceMember> group) {
        long min = group.stream().mapToLong(WorkforceMember::compaRatioBp).min().orElseThrow();
        long max = group.stream().mapToLong(WorkforceMember::compaRatioBp).max().orElseThrow();
        if (max - min < WIDE_SPREAD_MIN_BP) {
            return Optional.empty();
        }
        WorkforceMember first = group.getFirst();
        long outside = group.stream().filter(m -> m.bandPosition() != BandPosition.WITHIN).count();
        return Optional.of(new OutlierCard(
                id(OutlierType.WIDE_SPREAD, first), OutlierType.WIDE_SPREAD,
                severity(outside, max - min),
                "In %s, pay for %s ranges from %s to %s of band midpoint".formatted(first.countryName(),
                        first.jobTitle(), CompaRatio.fromBasisPoints(min), CompaRatio.fromBasisPoints(max)),
                "%d of %d are outside the band. Compare peers side by side.".formatted(outside, group.size()),
                outside, group.size(),
                filter("country", first.countryCode(), "title", first.jobTitle(), "sort", "compaRatio,asc")));
    }

    private static Optional<OutlierCard> departmentLag(List<WorkforceMember> group) {
        long averageBp = averageCompa(group);
        if (averageBp >= DEPARTMENT_LAG_MAX_BP) {
            return Optional.empty();
        }
        WorkforceMember first = group.getFirst();
        long percentBelow = BigDecimal.valueOf(10_000 - averageBp).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return Optional.of(new OutlierCard(
                OutlierType.DEPARTMENT_LAG + ":" + first.departmentId(), OutlierType.DEPARTMENT_LAG,
                severity(group.size(), 10_000 - averageBp),
                "%s is paid %d%% below band midpoint on average (%s)".formatted(first.departmentName(), percentBelow,
                        CompaRatio.fromBasisPoints(averageBp)),
                "Across %d active employees in all countries".formatted(group.size()),
                group.size(), group.size(),
                filter("department", String.valueOf(first.departmentId()), "sort", "compaRatio,asc")));
    }

    private static List<WorkforceMember> withPosition(List<WorkforceMember> group, BandPosition position) {
        return group.stream().filter(m -> m.bandPosition() == position).toList();
    }

    private static long averageCompa(List<WorkforceMember> members) {
        return PayStatistics.of(members.stream().map(WorkforceMember::compaRatioBp).toList()).average();
    }

    /** People × compa-ratio points of deviation (basis points / 100), rounded half up. */
    private static long severity(long people, long deviationBp) {
        return (people * deviationBp + 50) / 100;
    }

    private static String id(OutlierType type, WorkforceMember m) {
        return type + ":" + m.countryCode() + ":" + m.jobTitle();
    }

    private static Map<String, String> filter(String... keysAndValues) {
        Map<String, String> filter = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            filter.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return filter;
    }

    /** Sorted keys so the output never depends on input order. */
    private static <K extends Comparable<? super K>> Map<K, List<WorkforceMember>> groupBy(
            List<WorkforceMember> members, Function<WorkforceMember, K> key) {
        return members.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }
}
