package com.acme.salary.insights;

import com.acme.salary.employee.JobLevel;
import com.acme.salary.insights.OutlierDetector.OutlierCard;
import com.acme.salary.insights.OutlierDetector.OutlierType;
import com.acme.salary.payband.BandPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutlierDetectorTest {

    // --- underpaid / overpaid clusters ---

    @Test
    void flagsATitleWhereAtLeast15PercentArePaidBelowBand() {
        // 20 engineers in India, 3 below band (15%) at 0.75 and 0.77 and 0.76
        var cards = detect(group("IN", "Software Engineer", 1, fair(17), 7_500, 7_700, 7_600));

        OutlierCard card = only(cards, OutlierType.UNDERPAID_GROUP);
        assertThat(card.affected()).isEqualTo(3);
        assertThat(card.groupSize()).isEqualTo(20);
        assertThat(card.headline()).isEqualTo("In India, 3 of 20 people in Software Engineer roles are paid below their pay band");
        assertThat(card.detail()).contains("0.76");
        assertThat(card.severity()).isEqualTo(3 * 24);                       // 3 people x 24 compa points
        assertThat(card.filter()).containsExactly(
                Map.entry("country", "IN"), Map.entry("title", "Software Engineer"),
                Map.entry("bandPosition", "BELOW"), Map.entry("sort", "compaRatio,asc"));
    }

    @Test
    void ignoresGroupsBelowTheUnderpaidThreshold() {
        // 2 of 20 = 10% below band
        assertThat(detect(group("IN", "Software Engineer", 1, fair(18), 7_500, 7_500)))
                .noneMatch(c -> c.type() == OutlierType.UNDERPAID_GROUP);
    }

    @Test
    void ignoresGroupsTooSmallToBeMeaningful() {
        // 4 people, all below band: still not reported (noise, and identifiable individuals)
        assertThat(detect(group("SG", "Head of Design", 3, List.of(), 7_000, 7_000, 7_000, 7_000))).isEmpty();
    }

    @Test
    void flagsATitleWhereAtLeast20PercentArePaidAboveBand() {
        var cards = detect(group("DE", "Account Executive", 4, fair(8), 12_500, 12_300));

        OutlierCard card = only(cards, OutlierType.OVERPAID_GROUP);
        assertThat(card.affected()).isEqualTo(2);
        assertThat(card.severity()).isEqualTo(2 * 24);
        assertThat(card.filter()).containsEntry("bandPosition", "ABOVE").containsEntry("sort", "compaRatio,desc");
    }

    // --- spread ---

    @Test
    void flagsWidePaySpreadWithinATitle() {
        // range 0.78 .. 1.30 = 52 points; 2 people outside the band
        var cards = detect(group("GB", "Staff Engineer", 1, fair(10), 7_800, 13_000));

        OutlierCard card = only(cards, OutlierType.WIDE_SPREAD);
        assertThat(card.headline()).isEqualTo("In United Kingdom, pay for Staff Engineer ranges from 0.78 to 1.30 of band midpoint");
        assertThat(card.affected()).isEqualTo(2);
        assertThat(card.severity()).isEqualTo(2 * 52);
        assertThat(card.filter()).containsEntry("sort", "compaRatio,asc").doesNotContainKey("bandPosition");
    }

    @Test
    void toleratesNormalSpread() {
        // range 0.85 .. 1.15 = 30 points
        assertThat(detect(group("GB", "Staff Engineer", 1, fair(10), 8_500, 11_500)))
                .noneMatch(c -> c.type() == OutlierType.WIDE_SPREAD);
    }

    // --- department lag ---

    @Test
    void flagsADepartmentPaidWellBelowMidpointAcrossTheOrg() {
        List<WorkforceMember> people = new ArrayList<>();
        people.addAll(group("US", "Operations Analyst", 8, List.of(), 8_600, 8_700, 8_800));
        people.addAll(group("IN", "Operations Analyst", 8, List.of(), 8_500, 8_900, 8_700, 8_800));

        OutlierCard card = only(detect(people), OutlierType.DEPARTMENT_LAG);
        assertThat(card.headline()).isEqualTo("Operations is paid 13% below band midpoint on average (0.87)");
        assertThat(card.groupSize()).isEqualTo(7);
        assertThat(card.filter()).containsExactly(Map.entry("department", "8"), Map.entry("sort", "compaRatio,asc"));
    }

    // --- ranking ---

    @Test
    void ranksBySeverityAndHonoursTheLimit() {
        List<WorkforceMember> people = new ArrayList<>();
        people.addAll(group("IN", "Software Engineer", 1, fair(17), 7_500, 7_700, 7_600));    // severity 72
        people.addAll(group("JP", "Software Engineer", 1, fair(8), 7_000, 7_000, 7_000, 7_000)); // severity 120
        people.addAll(group("DE", "Account Executive", 4, fair(8), 12_500, 12_300));           // severity 48

        var cards = new OutlierDetector().detect(snapshot(people), 2);

        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(OutlierCard::severity).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(cards.getFirst().filter()).containsEntry("country", "JP");
    }

    @Test
    void isDeterministic() {
        List<WorkforceMember> people = new ArrayList<>();
        people.addAll(group("IN", "Software Engineer", 1, fair(17), 7_500, 7_700, 7_600));
        people.addAll(group("GB", "Staff Engineer", 1, fair(10), 7_800, 13_000));

        assertThat(detect(people)).isEqualTo(detect(people.reversed()));
    }

    // --- helpers ---

    private static List<OutlierCard> detect(List<WorkforceMember> people) {
        return new OutlierDetector().detect(snapshot(people), 20);
    }

    private static OutlierCard only(List<OutlierCard> cards, OutlierType type) {
        return cards.stream().filter(c -> c.type() == type).reduce((a, b) -> {
            throw new AssertionError("more than one " + type);
        }).orElseThrow(() -> new AssertionError("no " + type + " in " + cards));
    }

    private static WorkforceSnapshot snapshot(List<WorkforceMember> people) {
        return new WorkforceSnapshot(people, Instant.EPOCH, "2026-10-01");
    }

    /** n people at exactly the band midpoint. */
    private static List<Long> fair(int n) {
        return java.util.Collections.nCopies(n, 10_000L);
    }

    private static List<WorkforceMember> group(String country, String title, long dept, List<Long> base, long... extraCompaBp) {
        String countryName = Map.of("IN", "India", "DE", "Germany", "GB", "United Kingdom", "JP", "Japan",
                "US", "United States", "SG", "Singapore").get(country);
        String deptName = Map.of(1L, "Engineering", 3L, "Design", 4L, "Sales", 8L, "Operations").get(dept);
        List<Long> compa = new ArrayList<>(base);
        for (long bp : extraCompaBp) {
            compa.add(bp);
        }
        return compa.stream().map(bp -> new WorkforceMember(country, countryName, "XXX", dept, deptName, title,
                JobLevel.L3, bp * 10, bp, bp, bp < 8_000 ? BandPosition.BELOW : bp > 12_000 ? BandPosition.ABOVE
                : BandPosition.WITHIN)).toList();
    }
}
