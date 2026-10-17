package com.acme.salary.insights;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Equal-width buckets with "nice" boundaries (1, 2 or 5 × 10^n minor units), so a chart reads
 * "10,000 – 11,000" rather than "9,873.12 – 10,860.43". Buckets are half-open: [from, to).
 */
public final class Histogram {

    public record Bucket(long fromMinor, long toMinor, long count) {
    }

    private Histogram() {
    }

    public static List<Bucket> of(Collection<Long> values, int targetBuckets) {
        if (values.isEmpty()) {
            return List.of();
        }
        long min = values.stream().mapToLong(Long::longValue).min().orElseThrow();
        long max = values.stream().mapToLong(Long::longValue).max().orElseThrow();
        long range = max - min;
        long step = niceStep(range == 0
                ? Math.max(1, min / targetBuckets)
                : Math.ceilDiv(range, targetBuckets));
        long start = Math.floorDiv(min, step) * step;
        int bucketCount = (int) ((max - start) / step) + 1;

        long[] counts = new long[bucketCount];
        for (long v : values) {
            counts[(int) ((v - start) / step)]++;
        }
        List<Bucket> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new Bucket(start + i * step, start + (i + 1) * step, counts[i]));
        }
        return buckets;
    }

    /** Smallest value of the form 1, 2 or 5 × 10^n that is at least {@code atLeast}. */
    static long niceStep(long atLeast) {
        long magnitude = 1;
        while (true) {
            for (long factor : new long[]{1, 2, 5}) {
                if (factor * magnitude >= atLeast) {
                    return factor * magnitude;
                }
            }
            magnitude *= 10;
        }
    }
}
