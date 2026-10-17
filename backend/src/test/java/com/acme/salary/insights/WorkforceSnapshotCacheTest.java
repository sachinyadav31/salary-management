package com.acme.salary.insights;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceSnapshotCacheTest {

    private final AtomicInteger loads = new AtomicInteger();
    private final Supplier<WorkforceSnapshot> countingLoader = () ->
            new WorkforceSnapshot(List.of(), Instant.ofEpochSecond(loads.incrementAndGet()), "2026-10-01");

    @Test
    void servesTheCachedSnapshotUntilInvalidated() {
        WorkforceSnapshotCache cache = new WorkforceSnapshotCache(countingLoader);

        WorkforceSnapshot first = cache.get();
        assertThat(cache.get()).isSameAs(first);
        assertThat(loads).hasValue(1);

        cache.invalidate();
        assertThat(cache.get()).isNotSameAs(first);
        assertThat(loads).hasValue(2);
    }

    @Test
    void aLoadThatOverlapsAnInvalidationIsNotServedAgain() {
        // Simulates: reader starts loading, a write commits mid-load, reader finishes with now-stale data.
        AtomicInteger calls = new AtomicInteger();
        WorkforceSnapshotCache[] holder = new WorkforceSnapshotCache[1];
        holder[0] = new WorkforceSnapshotCache(() -> {
            if (calls.incrementAndGet() == 1) {
                holder[0].invalidate();
            }
            return countingLoader.get();
        });
        WorkforceSnapshotCache cache = holder[0];

        WorkforceSnapshot raced = cache.get();
        WorkforceSnapshot next = cache.get();

        assertThat(next).as("stale load must not be served from cache").isNotSameAs(raced);
        assertThat(cache.get()).isSameAs(next);
        assertThat(loads).hasValue(2);
    }
}
