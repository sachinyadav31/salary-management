package com.acme.salary.insights;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayStatisticsTest {

    @Test
    void computesMinMaxSumAverageAndMedianForOddCount() {
        PayStatistics stats = PayStatistics.of(List.of(500L, 100L, 300L));

        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.min()).isEqualTo(100);
        assertThat(stats.max()).isEqualTo(500);
        assertThat(stats.sum()).isEqualTo(900);
        assertThat(stats.average()).isEqualTo(300);
        assertThat(stats.median()).isEqualTo(300);
    }

    @Test
    void medianOfEvenCountIsTheMeanOfTheMiddleTwo() {
        assertThat(PayStatistics.of(List.of(100L, 200L, 400L, 1000L)).median()).isEqualTo(300);
    }

    @Test
    void halfMinorUnitsRoundHalfEven() {
        // (100 + 101) / 2 = 100.5 -> 100 (even); (101 + 102) / 2 = 101.5 -> 102 (even)
        assertThat(PayStatistics.of(List.of(100L, 101L)).median()).isEqualTo(100);
        assertThat(PayStatistics.of(List.of(101L, 102L)).median()).isEqualTo(102);
        // average 1001 / 2 = 500.5 -> 500
        assertThat(PayStatistics.of(List.of(500L, 501L)).average()).isEqualTo(500);
    }

    @Test
    void singleValueIsItsOwnEverything() {
        PayStatistics stats = PayStatistics.of(List.of(42L));

        assertThat(List.of(stats.min(), stats.max(), stats.average(), stats.median())).containsOnly(42L);
    }

    @Test
    void failsLoudlyInsteadOfSilentlyOverflowing() {
        assertThatThrownBy(() -> PayStatistics.of(List.of(Long.MAX_VALUE, Long.MAX_VALUE)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void emptyInputIsRejected() {
        assertThatThrownBy(() -> PayStatistics.of(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
