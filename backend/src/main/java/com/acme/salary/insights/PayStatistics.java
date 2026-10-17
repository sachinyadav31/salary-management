package com.acme.salary.insights;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Summary statistics over salaries in minor units of one currency. Exact integer maths;
 * averages and even-count medians round half-even to the nearest minor unit. A sum that does not fit in a
 * {@code long} throws rather than wrapping silently.
 */
public record PayStatistics(int count, long min, long max, long sum, long average, long median) {

    public static PayStatistics of(Collection<Long> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Statistics need at least one value");
        }
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        int n = sorted.length;
        BigInteger total = BigInteger.ZERO;
        for (long v : sorted) {
            total = total.add(BigInteger.valueOf(v));
        }
        long median = n % 2 == 1
                ? sorted[n / 2]
                : halfEven(BigInteger.valueOf(sorted[n / 2 - 1]).add(BigInteger.valueOf(sorted[n / 2])), 2);
        return new PayStatistics(n, sorted[0], sorted[n - 1], total.longValueExact(), halfEven(total, n), median);
    }

    private static long halfEven(BigInteger numerator, long denominator) {
        return new BigDecimal(numerator)
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_EVEN)
                .longValueExact();
    }
}
