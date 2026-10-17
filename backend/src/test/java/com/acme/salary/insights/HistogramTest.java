package com.acme.salary.insights;

import com.acme.salary.insights.Histogram.Bucket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class HistogramTest {

    @Test
    void usesNiceBucketWidthsAndBoundaries() {
        // 9,873.12 .. 19,746.24 in cents, ~10 buckets -> width 1,000.00, starting at 9,000.00
        List<Bucket> buckets = Histogram.of(List.of(987_312L, 1_974_624L), 10);

        assertThat(buckets.getFirst().fromMinor()).isEqualTo(900_000);
        assertThat(buckets).allSatisfy(b -> assertThat(b.toMinor() - b.fromMinor()).isEqualTo(100_000));
        assertThat(buckets.getLast().toMinor()).isGreaterThan(1_974_624);
    }

    @Test
    void everyValueLandsInExactlyOneBucket() {
        List<Long> values = LongStream.rangeClosed(1, 1_000).map(i -> i * 7_919 % 500_000 + 1).boxed().toList();

        List<Bucket> buckets = Histogram.of(values, 12);

        assertThat(buckets.stream().mapToLong(Bucket::count).sum()).isEqualTo(1_000);
        for (int i = 1; i < buckets.size(); i++) {
            assertThat(buckets.get(i).fromMinor()).isEqualTo(buckets.get(i - 1).toMinor());
        }
    }

    @Test
    void bucketsAreHalfOpenSoBoundaryValuesGoUp() {
        List<Bucket> buckets = Histogram.of(List.of(0L, 100L, 200L, 1_000L), 10); // width 100

        assertThat(buckets.getFirst()).isEqualTo(new Bucket(0, 100, 1));   // 0
        assertThat(buckets.get(1)).isEqualTo(new Bucket(100, 200, 1));     // 100
        assertThat(buckets.getLast()).isEqualTo(new Bucket(1_000, 1_100, 1));
    }

    @Test
    void identicalValuesProduceOneBucket() {
        assertThat(Histogram.of(List.of(5_000L, 5_000L, 5_000L), 10))
                .singleElement().satisfies(b -> assertThat(b.count()).isEqualTo(3));
    }

    @Test
    void niceStepsAreOneTwoOrFiveTimesAPowerOfTen() {
        assertThat(Histogram.niceStep(1)).isEqualTo(1);
        assertThat(Histogram.niceStep(13)).isEqualTo(20);
        assertThat(Histogram.niceStep(37)).isEqualTo(50);
        assertThat(Histogram.niceStep(74)).isEqualTo(100);
        assertThat(Histogram.niceStep(1_250_000)).isEqualTo(2_000_000);
    }

    @Test
    void emptyInputGivesNoBuckets() {
        assertThat(Histogram.of(List.of(), 10)).isEmpty();
    }
}
